package com.coefficientreader.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URI
import kotlin.math.abs
import kotlin.math.roundToInt

data class OddsResult(
    val decimal: Double,
    val fractional: String,
    val american: String,
    val probability: String
)

object OddsConverter {
    fun convert(value: Double): OddsResult? {
        if (!value.isFinite() || value <= 1.0 || value > 10000.0) return null
        val profit = value - 1.0
        val (n, d) = approximateFraction(profit)
        val american = if (value >= 2.0) {
            "+${(profit * 100.0).roundToInt()}"
        } else {
            "-${(100.0 / profit).roundToInt()}"
        }
        return OddsResult(
            decimal = value,
            fractional = "$n/$d",
            american = american,
            probability = "${"%.2f".format(java.util.Locale.US, 100.0 / value)}%"
        )
    }

    private fun approximateFraction(value: Double): Pair<Int, Int> {
        var bestN = 1
        var bestD = 1
        var bestError = Double.MAX_VALUE
        for (denominator in 1..1000) {
            val numerator = (value * denominator).roundToInt()
            val error = abs(value - numerator.toDouble() / denominator)
            if (error < bestError) {
                bestError = error
                bestN = numerator
                bestD = denominator
            }
            if (error < 0.000001) break
        }
        val gcd = gcd(bestN, bestD)
        return bestN / gcd to bestD / gcd
    }

    private fun gcd(a: Int, b: Int): Int =
        if (b == 0) a.coerceAtLeast(1) else gcd(b, a % b)
}

class MainActivity : ComponentActivity() {
    private var pageTextHandler: ((String) -> Unit)? = null

    inner class PageBridge {
        @JavascriptInterface
        fun onVisibleText(text: String) {
            runOnUiThread { pageTextHandler?.invoke(text.take(12000)) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var url by remember { mutableStateOf("https://") }
                var manualOdds by remember { mutableStateOf("") }
                var currentPage by remember { mutableStateOf("No page loaded") }
                var pageCandidates by remember { mutableStateOf<List<OddsResult>>(emptyList()) }
                var manualResult by remember { mutableStateOf<OddsResult?>(null) }
                var webView by remember { mutableStateOf<WebView?>(null) }

                pageTextHandler = { text ->
                    val regex = Regex("""(?<![\d.])(?:[1-9]\d{0,3})(?:\.\d{1,4})(?![\d.])""")
                    pageCandidates = regex.findAll(text)
                        .mapNotNull { it.value.toDoubleOrNull()?.let(OddsConverter::convert) }
                        .distinctBy { it.decimal }
                        .take(12)
                        .toList()
                }

                DisposableEffect(Unit) {
                    onDispose {
                        pageTextHandler = null
                        webView?.destroy()
                    }
                }

                Scaffold { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text("CoefficientReader", style = MaterialTheme.typography.headlineMedium)
                            Text("Read visible coefficients and convert formats.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        item {
                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Website URL") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val entered = url.trim()
                                    val normalized = if (entered.startsWith("https://") || entered.startsWith("http://")) entered else "https://$entered"
                                    val valid = runCatching {
                                        val uri = URI(normalized)
                                        (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()
                                    }.getOrDefault(false)
                                    if (valid) {
                                        url = normalized
                                        webView?.loadUrl(normalized)
                                    } else currentPage = "Enter a valid website address"
                                }) { Text("Open") }
                                OutlinedButton(onClick = { webView?.reload() }) { Text("Reload") }
                            }
                            Text(currentPage, style = MaterialTheme.typography.bodySmall)
                        }
                        item {
                            Text("In-app browser", style = MaterialTheme.typography.titleMedium)
                            AndroidView(
                                modifier = Modifier.fillMaxWidth().height(360.dp),
                                factory = { context ->
                                    WebView(context).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        webChromeClient = WebChromeClient()
                                        addJavascriptInterface(PageBridge(), "CoefficientBridge")
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageFinished(view: WebView, pageUrl: String) {
                                                currentPage = pageUrl
                                                view.evaluateJavascript(
                                                    """
                                                    (function() {
                                                      if (window.__coefficientReaderObserver) return;
                                                      window.__coefficientReaderObserver = true;
                                                      function sendText() {
                                                        try {
                                                          if (window.CoefficientBridge) {
                                                            window.CoefficientBridge.onVisibleText(document.body ? document.body.innerText : '');
                                                          }
                                                        } catch (e) {}
                                                      }
                                                      sendText();
                                                      const observer = new MutationObserver(sendText);
                                                      observer.observe(document.documentElement, {subtree:true, childList:true, characterData:true});
                                                    })();
                                                    """.trimIndent(),
                                                    null
                                                )
                                            }
                                        }
                                        webView = this
                                    }
                                },
                                update = { }
                            )
                            Text(
                                "Candidate values are detected from visible page text only. Verify each value against its market; page numbers and unrelated decimals may also appear.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        item {
                            Text("Convert a coefficient", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = manualOdds,
                                onValueChange = { manualOdds = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Decimal odds (e.g. 1.85)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                            Button(onClick = {
                                manualResult = manualOdds.trim().replace(',', '.').toDoubleOrNull()?.let(OddsConverter::convert)
                            }) { Text("Convert") }
                            manualResult?.let { ResultCard(it) }
                            if (manualOdds.isNotBlank() && manualResult == null) {
                                Text("Enter a decimal coefficient greater than 1.00.", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        item {
                            Text("Detected candidates", style = MaterialTheme.typography.titleMedium)
                            Text("Experimental visible-text scan · not a verified market feed", style = MaterialTheme.typography.bodySmall)
                        }
                        if (pageCandidates.isEmpty()) {
                            item { Text("No candidate coefficients detected yet.") }
                        } else {
                            items(pageCandidates) { result -> ResultCard(result) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResultCard(result: OddsResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Decimal: ${"%.3f".format(java.util.Locale.US, result.decimal)}", style = MaterialTheme.typography.titleMedium)
            Text("Fractional: ${result.fractional}")
            Text("American: ${result.american}")
            Text("Implied probability: ${result.probability}")
        }
    }
}
