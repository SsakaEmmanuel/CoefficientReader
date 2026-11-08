package com.coefficientreader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.net.URI
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val background = Color(0xFF0B0D13)
            val surface = Color(0xFF171B25)
            val raised = Color(0xFF202634)
            val accent = Color(0xFFFF3545)
            val mint = Color(0xFF42D6A4)
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = accent,
                    secondary = mint,
                    background = background,
                    surface = surface,
                    onBackground = Color(0xFFF4F5FA),
                    onSurface = Color(0xFFF4F5FA)
                )
            ) {
                val engine = remember { FeedEngine() }
                val scope = rememberCoroutineScope()
                var feedUrl by remember { mutableStateOf("") }
                var apiToken by remember { mutableStateOf("") }
                var mode by remember { mutableStateOf(FeedMode.JSON_API) }
                var refreshSeconds by remember { mutableStateOf("3") }
                var status by remember { mutableStateOf("Ready · connect an authorised data feed") }
                var connected by remember { mutableStateOf(false) }
                var hits by remember { mutableStateOf<List<CoefficientHit>>(emptyList()) }
                var feedJob by remember { mutableStateOf<Job?>(null) }
                var updateCount by remember { mutableIntStateOf(0) }

                DisposableEffect(Unit) {
                    onDispose { feedJob?.cancel() }
                }

                Scaffold(containerColor = background) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 30.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        modifier = Modifier.size(44.dp).background(accent, RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("CR", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                                    }
                                    Column {
                                        Text("COEFFICIENT", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.1.sp)
                                        Text("READER  •  ADVANCED", color = Color(0xFFAAB1C1), fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Surface(
                                    color = if (connected) Color(0xFF173A32) else Color(0xFF30242A),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        if (connected) "● CONNECTED" else "● STANDBY",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        color = if (connected) mint else Color(0xFFFF9AA2),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(26.dp),
                                colors = CardDefaults.cardColors(containerColor = surface)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("LIVE COEFFICIENT", color = Color(0xFFB8C0D0), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                        Text("NATIVE FEED", color = mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(
                                        modifier = Modifier.size(224.dp).background(Color(0xFF401E28), CircleShape).padding(7.dp)
                                            .background(accent, CircleShape).padding(5.dp).background(Color(0xFF11141D), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                hits.firstOrNull()?.decimal?.let { String.format(Locale.US, "%.2f", it) } ?: "--",
                                                color = accent,
                                                fontSize = 48.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            Text("DECIMAL ODDS", color = Color.White, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                                            Text(if (connected) "STREAM ACTIVE" else "WAITING FOR FEED", color = if (connected) mint else Color(0xFF9AA3B5), fontSize = 9.sp)
                                        }
                                    }
                                    Text(status, color = Color(0xFFB8C0D0), fontSize = 12.sp)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        Metric("UPDATES", updateCount.toString(), mint)
                                        Metric("DETECTED", hits.size.toString(), Color.White)
                                        Metric("MODE", mode.label, accent)
                                    }
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = surface)
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("DATA SOURCE", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Connect directly to an API or live feed. This engine does not scrape numbers from a website's visible screen.",
                                        color = Color(0xFFAAB1C1), fontSize = 12.sp
                                    )
                                    OutlinedTextField(
                                        value = feedUrl,
                                        onValueChange = { feedUrl = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Feed endpoint URL") },
                                        placeholder = {
                                            Text(when (mode) {
                                                FeedMode.JSON_API -> "https://api.example.com/odds"
                                                FeedMode.SSE -> "https://api.example.com/odds/stream"
                                                FeedMode.WEBSOCKET -> "wss://stream.example.com/odds"
                                            })
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = accent,
                                            focusedLabelColor = accent,
                                            cursorColor = accent,
                                            unfocusedBorderColor = Color(0xFF3A4354)
                                        )
                                    )
                                    Text("CONNECTION TYPE", color = Color(0xFFAAB1C1), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FeedMode.values().forEach { option ->
                                            val selected = mode == option
                                            Surface(
                                                modifier = Modifier.weight(1f),
                                                onClick = { mode = option },
                                                color = if (selected) Color(0xFF51212D) else raised,
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(
                                                    option.label,
                                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                                    color = if (selected) Color.White else Color(0xFFAAB1C1),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = apiToken,
                                        onValueChange = { apiToken = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("API token / key (optional)") },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = accent,
                                            focusedLabelColor = accent,
                                            cursorColor = accent,
                                            unfocusedBorderColor = Color(0xFF3A4354)
                                        )
                                    )
                                    if (mode == FeedMode.JSON_API) {
                                        OutlinedTextField(
                                            value = refreshSeconds,
                                            onValueChange = { refreshSeconds = it.filter(Char::isDigit).take(2) },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("Auto refresh interval (seconds)") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = accent,
                                                focusedLabelColor = accent,
                                                cursorColor = accent,
                                                unfocusedBorderColor = Color(0xFF3A4354)
                                            )
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        Button(
                                            onClick = {
                                                val raw = feedUrl.trim()
                                                val uri = runCatching { URI(raw) }.getOrNull()
                                                val valid = when (mode) {
                                                    FeedMode.JSON_API, FeedMode.SSE -> uri?.scheme == "https" && !uri.host.isNullOrBlank()
                                                    FeedMode.WEBSOCKET -> uri?.scheme == "wss" && !uri.host.isNullOrBlank()
                                                }
                                                if (!valid) {
                                                    status = when (mode) {
                                                        FeedMode.JSON_API, FeedMode.SSE -> "Use a valid HTTPS feed endpoint."
                                                        FeedMode.WEBSOCKET -> "Use a secure WSS endpoint."
                                                    }
                                                } else {
                                                    feedJob?.cancel()
                                                    hits = emptyList()
                                                    updateCount = 0
                                                    connected = true
                                                    status = "Connecting to authorised feed…"
                                                    val config = FeedConfig(
                                                        url = raw,
                                                        mode = mode,
                                                        token = apiToken.trim(),
                                                        refreshSeconds = refreshSeconds.toIntOrNull()?.coerceIn(2, 60) ?: 3
                                                    )
                                                    feedJob = scope.launch {
                                                        engine.connect(config).collect { event ->
                                                            when (event) {
                                                                is FeedEvent.Status -> {
                                                                    status = event.message
                                                                    connected = event.message.contains("connected", true) &&
                                                                        !event.message.contains("reconnecting", true) &&
                                                                        !event.message.contains("failed", true)
                                                                }
                                                                is FeedEvent.Data -> {
                                                                    if (event.hits.isNotEmpty()) {
                                                                        hits = event.hits
                                                                        updateCount += 1
                                                                        status = "New structured price data received"
                                                                        connected = true
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text("CONNECT FEED", fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                feedJob?.cancel()
                                                feedJob = null
                                                connected = false
                                                status = "Feed stopped"
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text("STOP")
                                        }
                                    }
                                    Text(
                                        "API keys stay in memory for this session and are not saved by the app. Use only endpoints and credentials you are authorised to access.",
                                        color = Color(0xFF8993A6), fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("DETECTED PRICE FIELDS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${hits.size} found", color = Color(0xFF9CA7BA), fontSize = 11.sp)
                            }
                        }
                        if (hits.isEmpty()) {
                            item {
                                Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(16.dp)) {
                                    Text(
                                        "No structured odds fields received yet. Connect a JSON, SSE or WebSocket endpoint that supplies decimal price fields.",
                                        modifier = Modifier.padding(16.dp),
                                        color = Color(0xFFAAB1C1), fontSize = 12.sp
                                    )
                                }
                            }
                        } else {
                            items(hits) { hit ->
                                Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(15.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(hit.field, color = Color(0xFFB5BED0), fontSize = 11.sp, maxLines = 2)
                                            Text("DECIMAL PRICE", color = Color(0xFF778195), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text(String.format(Locale.US, "%.3f", hit.decimal), color = accent, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                "Reader support: native HTTPS JSON polling, SSE and WSS streams. Site-specific authentication, undocumented endpoints, proprietary encodings and protected feeds require an authorised adapter; a homepage URL alone is not a data feed.",
                                color = Color(0xFF778195), fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Color(0xFF8993A6), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
