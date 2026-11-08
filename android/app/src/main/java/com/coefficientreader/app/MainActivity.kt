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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URI
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class Odds(val decimal: Double, val fractional: String, val american: String, val probability: String)

object OddsConverter {
    fun convert(value: Double): Odds? {
        if (!value.isFinite() || value <= 1.0 || value > 10000.0) return null
        val profit = value - 1.0
        val (n, d) = fraction(profit)
        val american = if (value >= 2.0) "+${(profit * 100).roundToInt()}" else "-${(100.0 / profit).roundToInt()}"
        return Odds(value, "$n/$d", american, String.format(Locale.US, "%.2f%%", 100.0 / value))
    }
    private fun fraction(value: Double): Pair<Int, Int> {
        var bestN = 1; var bestD = 1; var bestError = Double.MAX_VALUE
        for (denominator in 1..1000) {
            val numerator = (value * denominator).roundToInt()
            val error = abs(value - numerator.toDouble() / denominator)
            if (error < bestError) { bestError = error; bestN = numerator; bestD = denominator }
            if (error < 0.000001) break
        }
        val divisor = gcd(bestN, bestD)
        return bestN / divisor to bestD / divisor
    }
    private fun gcd(a: Int, b: Int): Int = if (b == 0) a.coerceAtLeast(1) else gcd(b, a % b)
}

class MainActivity : ComponentActivity() {
    private var textCallback: ((String) -> Unit)? = null
    inner class PageBridge {
        @JavascriptInterface fun sendVisibleText(value: String) {
            runOnUiThread { textCallback?.invoke(value.take(12000)) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var address by remember { mutableStateOf("") }
                var manual by remember { mutableStateOf("") }
                var status by remember { mutableStateOf("Enter a website URL.") }
                var webView by remember { mutableStateOf<WebView?>(null) }
                var candidates by remember { mutableStateOf<List<Odds>>(emptyList()) }
                var result by remember { mutableStateOf<Odds?>(null) }

                textCallback = { pageText ->
                    val regex = Regex("""(?<![\d.])(?:[1-9]\d{0,3})\.\d{1,4}(?![\d.])""")
                    candidates = regex.findAll(pageText)
                        .mapNotNull { it.value.toDoubleOrNull()?.let(OddsConverter::convert) }
                        .distinctBy { it.decimal }.take(12).toList()
                }
                DisposableEffect(Unit) {
                    onDispose { textCallback = null; webView?.destroy() }
                }

                Scaffold { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text("CoefficientReader", style = MaterialTheme.typography.headlineMedium)
                            Text("Browser-based coefficient reader and converter.")
                        }
                        item {
                            OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text("Website URL") }, singleLine = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val raw = address.trim()
                                    val full = if (raw.startsWith("https://") || raw.startsWith("http://")) raw else "https://$raw"
                                    val valid = runCatching {
                                        URI(full).let { (it.scheme == "https" || it.scheme == "http") && !it.host.isNullOrBlank() }
                                    }.getOrDefault(false)
                                    if (valid) { address = full; webView?.loadUrl(full) } else status = "Enter a valid URL."
                                }) { Text("Open") }
                                OutlinedButton(onClick = { webView?.reload() }) { Text("Reload") }
                            }
                            Text(status, style = MaterialTheme.typography.bodySmall)
                        }
                        item {
                            Text("In-app browser", style = MaterialTheme.typography.titleMedium)
                            AndroidView(
                                modifier = Modifier.fillMaxWidth().height(340.dp),
                                factory = { context ->
                                    WebView(context).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        webChromeClient = WebChromeClient()
                                        addJavascriptInterface(PageBridge(), "CoefficientBridge")
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageFinished(view: WebView, url: String) {
                                                status = "Loaded: $url"
                                                view.evaluateJavascript("""
                                                    (function() {
                                                      if (window.__coefficientReaderObserver) return;
                                                      window.__coefficientReaderObserver = true;
                                                      const send = function() {
                                                        try { window.CoefficientBridge.sendVisibleText(document.body ? document.body.innerText : ''); } catch(e) {}
                                                      };
                                                      send();
                                                      new MutationObserver(send).observe(document.documentElement, {subtree:true, childList:true, characterData:true});
                                                    })();
                                                """.trimIndent(), null)
                                            }
                                        }
                                        webView = this
                                    }
                                }
                            )
                            Text("Only visible page text is scanned. Unrelated numbers may be detected; verify every candidate against its market.", style = MaterialTheme.typography.bodySmall)
                        }
                        item {
                            Text("Manual conversion", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(manual, { manual = it }, Modifier.fillMaxWidth(), label = { Text("Decimal odds, e.g. 1.85") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                            Button(onClick = { result = manual.trim().replace(',', '.').toDoubleOrNull()?.let(OddsConverter::convert) }) { Text("Convert") }
                            result?.let { OddsCard(it) }
                            if (manual.isNotBlank() && result == null) Text("Enter decimal odds greater than 1.00.", color = MaterialTheme.colorScheme.error)
                        }
                        item {
                            Text("Detected candidates", style = MaterialTheme.typography.titleMedium)
                            Text("Experimental scanner · candidate values only", style = MaterialTheme.typography.bodySmall)
                        }
                        if (candidates.isEmpty()) item { Text("No candidates detected yet.") }
                        else items(candidates) { OddsCard(it) }
                    }
                }
            }
        }
    }
}

@Composable
fun OddsCard(odds: Odds) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(String.format(Locale.US, "Decimal: %.3f", odds.decimal), style = MaterialTheme.typography.titleMedium)
            Text("Fractional: ${odds.fractional}")
            Text("American: ${odds.american}")
            Text("Implied probability: ${odds.probability}")
        }
    }
}
