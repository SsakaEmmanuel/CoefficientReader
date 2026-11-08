package com.coefficientreader.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.net.URI
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var pageTextCallback: ((String) -> Unit)? = null

    inner class PageBridge {
        @JavascriptInterface
        fun sendVisibleText(value: String) {
            runOnUiThread { pageTextCallback?.invoke(value.take(20000)) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val bg = Color(0xFF101018)
            val panel = Color(0xFF1C1C28)
            val red = Color(0xFFFF3038)
            MaterialTheme(colorScheme = darkColorScheme(
                primary = red,
                background = bg,
                surface = panel,
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                var urlInput by remember { mutableStateOf("") }
                var activeUrl by remember { mutableStateOf("") }
                var screen by remember { mutableStateOf("setup") }
                var status by remember { mutableStateOf("Enter the bookmaker website address to begin.") }
                var latestCoefficient by remember { mutableStateOf("--") }
                var lastUpdated by remember { mutableStateOf("Waiting for website data") }
                var webView by remember { mutableStateOf<WebView?>(null) }
                var scanActive by remember { mutableStateOf(false) }

                pageTextCallback = { visibleText ->
                    val oddsPattern = Regex("""(?<![\\d.])(?:[1-9]\\d{0,3})\\.\\d{1,4}(?![\\d.])""")
                    val values = oddsPattern.findAll(visibleText)
                        .mapNotNull { it.value.toDoubleOrNull() }
                        .filter { it > 1.0 && it <= 10000.0 }
                        .toList()
                    if (values.isNotEmpty()) {
                        latestCoefficient = String.format(Locale.US, "%.2f", values.first())
                        lastUpdated = "Page data received · live scan"
                    }
                }

                DisposableEffect(Unit) {
                    onDispose { pageTextCallback = null; webView?.destroy() }
                }

                LaunchedEffect(scanActive, webView) {
                    while (scanActive) {
                        webView?.evaluateJavascript(
                            "(function(){return document.body ? document.body.innerText : '';})()"
                        ) { encoded ->
                            val decoded = runCatching {
                                org.json.JSONTokener(encoded ?: "\"\"").nextValue() as? String
                            }.getOrNull()
                            if (!decoded.isNullOrBlank()) pageTextCallback?.invoke(decoded)
                        }
                        delay(3000)
                    }
                }

                Scaffold(containerColor = bg) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text("COEFFICIENT", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                                Text("READER", color = red, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                            }
                            Surface(color = Color(0xFF203A2C), shape = RoundedCornerShape(50)) {
                                Text(if (scanActive) "● LIVE" else "● OFFLINE", color = if (scanActive) Color(0xFF62E69A) else Color.LightGray, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                            }
                        }

                        if (screen == "setup") {
                            Text("Connect your website", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Enter the bookmaker URL. Sign in on the bookmaker's own page inside the app; CoefficientReader does not collect or store your password.", color = Color.LightGray)
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Website URL") },
                                placeholder = { Text("https://example.com") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = red,
                                    focusedLabelColor = red,
                                    cursorColor = red
                                )
                            )
                            Button(
                                onClick = {
                                    val raw = urlInput.trim()
                                    val full = if (raw.startsWith("https://") || raw.startsWith("http://")) raw else "https://$raw"
                                    val valid = runCatching {
                                        URI(full).let { (it.scheme == "https" || it.scheme == "http") && !it.host.isNullOrBlank() }
                                    }.getOrDefault(false)
                                    if (valid) {
                                        activeUrl = full
                                        status = "Opening bookmaker website…"
                                        screen = "browser"
                                        webView?.loadUrl(full)
                                    } else status = "Please enter a valid website URL."
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = red)
                            ) { Text("OPEN WEBSITE", fontWeight = FontWeight.Bold) }
                            Text(status, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.weight(1f))
                            Text("Your phone number and password are entered only on the bookmaker's own login page.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        } else {
                            if (screen == "browser") {
                                Text("Sign in to your bookmaker", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Complete sign-in on the website below. Then return here and start the reader.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { screen = "reader"; scanActive = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = red)) {
                                    Text("I'M SIGNED IN — START READER", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("LIVE COEFFICIENT", color = Color.LightGray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    TextButton(onClick = { scanActive = !scanActive }) { Text(if (scanActive) "Pause" else "Resume", color = red) }
                                }
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Box(
                                        modifier = Modifier.size(230.dp).background(Color(0xFF35191D), CircleShape).padding(7.dp).background(red, CircleShape).padding(5.dp).background(bg, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(latestCoefficient, color = red, fontSize = 48.sp, fontWeight = FontWeight.Black)
                                            Text("COEFFICIENT", color = Color.White, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(lastUpdated, color = Color(0xFF9B9BA8), style = MaterialTheme.typography.bodySmall)
                                Text("Automatic page scan every 3 seconds. Live page changes are also observed.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(onClick = { webView?.reload() }, modifier = Modifier.weight(1f)) { Text("Refresh site") }
                                    OutlinedButton(onClick = { screen = "browser"; scanActive = false }, modifier = Modifier.weight(1f)) { Text("Back to site") }
                                }
                            }

                            AndroidView(
                                modifier = Modifier.fillMaxWidth().weight(1f).background(panel, RoundedCornerShape(14.dp)),
                                factory = { context ->
                                    WebView(context).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.loadsImagesAutomatically = true
                                        webChromeClient = WebChromeClient()
                                        addJavascriptInterface(PageBridge(), "CoefficientBridge")
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageFinished(view: WebView, url: String) {
                                                status = "Website loaded"
                                                view.evaluateJavascript("""
                                                    (function() {
                                                      if (window.__coefficientReaderObserver) return;
                                                      window.__coefficientReaderObserver = true;
                                                      const send = function() {
                                                        try {
                                                          const text = document.body ? document.body.innerText : '';
                                                          window.CoefficientBridge.sendVisibleText(text);
                                                        } catch(e) {}
                                                      };
                                                      send();
                                                      new MutationObserver(send).observe(document.documentElement, {subtree:true, childList:true, characterData:true});
                                                    })();
                                                """.trimIndent(), null)
                                            }
                                        }
                                        webView = this
                                    }
                                },
                                update = { if (activeUrl.isNotBlank() && it.url == null && screen != "setup") it.loadUrl(activeUrl) }
                            )
                        }
                    }
                }
            }
        }
    }
}
