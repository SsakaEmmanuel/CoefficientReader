package com.coefficientreader.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class FeedMode(val label: String) {
    JSON_API("JSON / API"),
    SSE("SSE"),
    WEBSOCKET("WebSocket")
}

data class FeedConfig(
    val url: String,
    val mode: FeedMode,
    val token: String = "",
    val refreshSeconds: Int = 3
)

data class CoefficientHit(
    val decimal: Double,
    val field: String,
    val receivedAt: Long = System.currentTimeMillis()
)

sealed class FeedEvent {
    data class Status(val message: String) : FeedEvent()
    data class Data(val hits: List<CoefficientHit>) : FeedEvent()
}

object OddsPayloadParser {
    private val priceKey = Regex(
        "(?i).*(decimal_?odds|odds_?decimal|odds|coefficient|coef|back_?price|lay_?price|current_?price|price).*"
    )

    fun parse(payload: String): List<CoefficientHit> {
        val root: Any = try {
            JSONObject(payload)
        } catch (_: Exception) {
            try { JSONArray(payload) } catch (_: Exception) {
                val single = payload.trim().toDoubleOrNull()
                return if (single != null && single > 1.0 && single <= 1000.0) {
                    listOf(CoefficientHit(single, "stream value"))
                } else emptyList()
            }
        }

        val found = mutableListOf<CoefficientHit>()
        walk(root, "", found)
        return found.distinctBy { Pair(it.decimal, it.field) }.take(30)
    }

    private fun walk(node: Any?, path: String, found: MutableList<CoefficientHit>) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = node.opt(key)
                    val nextPath = if (path.isBlank()) key else "$path.$key"
                    if (priceKey.matches(key)) {
                        val number = when (value) {
                            is Number -> value.toDouble()
                            is String -> value.trim().replace(",", ".").toDoubleOrNull()
                            else -> null
                        }
                        if (number != null && number > 1.0 && number <= 1000.0) {
                            found.add(CoefficientHit(number, nextPath))
                        }
                    }
                    if (value is JSONObject || value is JSONArray) walk(value, nextPath, found)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                walk(node.opt(i), if (path.isBlank()) "[$i]" else "$path[$i]", found)
            }
        }
    }
}

class FeedEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun connect(config: FeedConfig): Flow<FeedEvent> = when (config.mode) {
        FeedMode.JSON_API -> jsonPolling(config)
        FeedMode.SSE -> sseStream(config)
        FeedMode.WEBSOCKET -> webSocketStream(config)
    }

    private fun requestBuilder(config: FeedConfig): Request.Builder {
        val builder = Request.Builder().url(config.url).header("Accept", "application/json, text/event-stream")
        if (config.token.isNotBlank()) {
            builder.header("Authorization", "Bearer ${config.token}")
            builder.header("X-API-Key", config.token)
        }
        return builder
    }

    private fun jsonPolling(config: FeedConfig): Flow<FeedEvent> = flow {
        while (currentCoroutineContext().isActive) {
            try {
                val payload = withContext(Dispatchers.IO) {
                    val call = client.newCall(requestBuilder(config).get().build())
                    val job = currentCoroutineContext().job
                    job.invokeOnCompletion { call.cancel() }
                    call.execute().use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        response.body?.string().orEmpty()
                    }
                }
                val hits = OddsPayloadParser.parse(payload)
                emit(FeedEvent.Data(hits))
                emit(FeedEvent.Status(if (hits.isEmpty()) "Connected · no recognised odds fields yet" else "Connected · ${hits.size} price field(s) detected"))
            } catch (error: Exception) {
                emit(FeedEvent.Status(error.message ?: "Feed request failed"))
            }
            delay(config.refreshSeconds.coerceIn(2, 60) * 1000L)
        }
    }

    private fun sseStream(config: FeedConfig): Flow<FeedEvent> = callbackFlow {
        var activeCall: Call? = null
        val worker = launch(Dispatchers.IO) {
            while (isActive) {
                val call = client.newCall(requestBuilder(config).header("Accept", "text/event-stream").build())
                activeCall = call
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            trySend(FeedEvent.Status("SSE connection returned HTTP ${response.code}"))
                        } else {
                            trySend(FeedEvent.Status("SSE connected · listening for updates"))
                            val source = response.body?.source()
                            val eventData = StringBuilder()
                            if (source != null) {
                                while (isActive) {
                                    val line = source.readUtf8Line() ?: break
                                    if (line.startsWith("data:")) {
                                        if (eventData.isNotEmpty()) eventData.append('\n')
                                        eventData.append(line.removePrefix("data:").trimStart())
                                    } else if (line.isEmpty() && eventData.isNotEmpty()) {
                                        val hits = OddsPayloadParser.parse(eventData.toString())
                                        trySend(FeedEvent.Data(hits))
                                        eventData.clear()
                                    }
                                }
                            }
                        }
                    }
                } catch (error: Exception) {
                    if (isActive) trySend(FeedEvent.Status(error.message ?: "SSE disconnected"))
                }
                activeCall = null
                if (isActive) {
                    trySend(FeedEvent.Status("Reconnecting to SSE feed…"))
                    delay(2000)
                }
            }
        }
        awaitClose {
            activeCall?.cancel()
            worker.cancel()
        }
    }

    private fun webSocketStream(config: FeedConfig): Flow<FeedEvent> = flow {
        while (currentCoroutineContext().isActive) {
            try {
                webSocketAttempt(config).collect { emit(it) }
            } catch (error: Exception) {
                emit(FeedEvent.Status(error.message ?: "WebSocket disconnected"))
            }
            if (currentCoroutineContext().isActive) {
                emit(FeedEvent.Status("Reconnecting to WebSocket feed…"))
                delay(2000)
            }
        }
    }

    private fun webSocketAttempt(config: FeedConfig): Flow<FeedEvent> = callbackFlow {
        val request = requestBuilder(config).build()
        val socket: WebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                trySend(FeedEvent.Status("WebSocket connected · live stream active"))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                trySend(FeedEvent.Data(OddsPayloadParser.parse(text)))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(FeedEvent.Status(t.message ?: "WebSocket connection failed"))
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        })
        awaitClose { socket.cancel() }
    }
}
