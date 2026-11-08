# CoefficientReader

Android coefficient reader prototype using Kotlin, Jetpack Compose and WebView.

## Starter features
- In-app browser for websites the user is authorized to access.
- Manual decimal odds conversion to fractional, American and implied probability.
- Experimental visible-page text candidate scanner.
- GitHub Actions debug APK build.

## Limitations
This is not a universal bookmaker integration. Different sites use different page structures and protocols. The visible-text scanner only suggests possible decimal odds and can pick up unrelated numbers. Verify candidates against their markets. It does not read private tokens or cookies, bypass access controls, or place bets.

The three-second transition period is an observation window, not a guarantee that a new market value will arrive within three seconds.

## Build
Open `android` in Android Studio, or run `gradle assembleDebug` from that folder. The APK is generated at `android/app/build/outputs/apk/debug/app-debug.apk`.

Package: `com.coefficientreader.app`
