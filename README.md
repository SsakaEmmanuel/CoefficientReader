# CoefficientReader

Native Android coefficient reader built with Kotlin and Jetpack Compose.

## Advanced feed reader

The current app reads structured data directly through native network connections. It no longer scans visible website text or uses a WebView as its odds-reading engine.

Supported transports:
- HTTPS REST/JSON polling, with a configurable refresh interval.
- Server-sent events (SSE).
- Secure WebSockets (WSS).

The JSON parser looks for decimal-price fields with names such as `odds`, `decimalOdds`, `coefficient`, `backPrice`, `layPrice`, and `price`. It shows the source field path and received decimal values in the app.

An optional API token can be supplied for feeds that require one. It is held in memory for the active session and is not stored by the app.

## Important compatibility limits

A bookmaker homepage URL is not necessarily a data-feed endpoint. To use the native reader, provide the authorised HTTPS JSON/SSE endpoint or WSS endpoint documented by that data provider. Some providers require API credentials, specific request headers, GraphQL queries, custom message formats, or an account-specific adapter. Those need explicit support; a generic reader cannot infer or access every private feed.

The app does not collect bookmaker passwords, extract session cookies, bypass CAPTCHAs, evade access controls, or place bets.

## Build

Open `android` in Android Studio, or run `gradle assembleDebug` from that folder. The debug APK is generated at `android/app/build/outputs/apk/debug/app-debug.apk`.

Package: `com.coefficientreader.app`
