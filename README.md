# Sanka Reader (Android / Kotlin)

Native Android comic reader using the official Sanka Comic REST API.

## Build
- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35
- Open the folder and sync Gradle, then run `:app:assembleDebug`.

## API
Base URL: `https://www.sankavollerei.web.id`
The app uses documented GET routes from the Comic API landing page. Provider-specific route templates live in `data/ProviderRegistry.kt` so they can be updated independently.

## Important API behavior
- The official Comic API landing page currently states **45 requests/minute**, warns about three warnings before a permanent ban, and notes that the API is still under development. The app uses a conservative client-side cap of 30 requests/minute and a 2-second minimum interval between requests.
- The landing page and JSON documentation have previously displayed different rate limits. Treat the current landing-page statement as the conservative limit; do not increase the cap without confirmation from Sanka.
- No Cloudflare/CAPTCHA/403 bypass is implemented. HTTP 403/429 responses are surfaced and the app backs off.
- Provider route patterns vary. A provider marked unavailable or unsupported is not silently substituted with a guessed endpoint.
- `icon.png` is a replaceable starter icon. Replace `app/src/main/res/drawable/icon.png` with your own 512x512 PNG; the same resource is used in the developer profile. Launcher icon uses the generated mipmap assets.

## Features
- Home slider, latest/popular sections, provider selector and search.
- Watchlist with favorites and reading history.
- Provider-aware endpoint registry with per-provider slug and query handling.
- Generic JSON normalizer for differing response envelopes.
- Reader with x1-x6 auto-scroll, next/previous chapter, auto-next, and immersive mode.
- Local persistence for favorites, history, and reader settings.
- WorkManager periodic refresh every 6 hours; requests are throttled and cache-first.

## Notes
Sanka's endpoints and response shapes can change. Validate live endpoints and individual provider detail/chapter responses before production release. A provider route listed in the official directory is not a guarantee that its upstream source is currently healthy. The app intentionally reports provider errors instead of bypassing access controls.

## Developer profile / icon
- Developer display name is currently `Nama Developer` in `ui/ReaderApp.kt`; replace it with the display name you want to publish.
- The same `app/src/main/res/drawable/icon.png` is referenced by the launcher manifest icon and the profile card. Replace that PNG to brand the app.

## Provider coverage and limits
- Active documented adapters: Komiku, BacaKomik, Maid, Komikindo, Mangakita, Meganei, Softkomik, Westmanga, Kmkindo, Kiryuu, and Shinigami.
- Komikstation is present in the registry but disabled because Sanka's current directory marks it as an error.
- Some names are listed in the Sanka page heading without a documented route set. No route is invented for those sources.
- Providers can return different IDs (slug, numeric ID, UUID, or chapter ID); the registry keeps the endpoint pattern separate and the reader stores the provider with the comic/chapter identifier.
- Adult-only source categories are not included. The client also filters common explicit-content labels where those labels are present in API metadata; API metadata is not guaranteed to be complete.

## Build verification
The source tree and Kotlin syntax were checked in this environment, but a full Android Gradle build/APK was not run because an Android SDK/Gradle installation was not available here. Open in Android Studio, install SDK 35 and JDK 17, sync Gradle, then build. Confirm each provider with live API data before publishing because Sanka marks the API as under development.
