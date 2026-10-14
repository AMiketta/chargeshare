# ChargeShare – a Compose Multiplatform sample app

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.12.1-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/compose-multiplatform/)
[![Ktor](https://img.shields.io/badge/Ktor-3.6.0-087CFA?logo=ktor&logoColor=white)](https://ktor.io)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS%20%7C%20Desktop%20%7C%20Web%20(Wasm)-0B6E4F)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Donate](https://img.shields.io/badge/Donate-PayPal-00457C?logo=paypal&logoColor=white)](https://paypal.me/Miketta)

🇩🇪 [Deutsche Version (detailed docs)](README.de.md)

**ChargeShare** is a peer-to-peer wallbox-sharing app ("Airbnb for EV chargers"): hosts list their private
wallbox, drivers find one on a map, book a time slot and pay in the app.

It is written **entirely in Kotlin** – one shared Compose Multiplatform UI for **Android, iOS, Desktop and Web (Wasm)**
plus a **Ktor backend** with PostgreSQL and Stripe. It is not a toy demo: it is a complete, real-world app that is
published here as a **reference / learning project** for Compose Multiplatform and full-stack Kotlin.

> Code comments and the detailed developer notes ([AGENTS.md](AGENTS.md), [README.de.md](README.de.md)) are mostly in German.

## Features

- Splash screen, onboarding, login/registration, password reset (deep link back into the app)
- Wallbox search with map (OpenStreetMap via MapCompose on Android/Desktop, MapKit on iOS), place search, favorites
- Wallbox details with photos, availability slots and booking flow
- Payments with Stripe (Google Pay, Apple Pay, card) – or a **fake payment provider** for local development
- Bookings, PDF receipts, host dashboard (manage wallboxes, photos, payouts via Stripe Connect)
- Profile with stats, light/dark/system theme
- Push notification groundwork (device registration; delivery is currently logged only)
- OCPP endpoint for charging sessions on the server

## Tech stack

| Area | Libraries |
|---|---|
| UI | Compose Multiplatform 1.12, Material 3, JetBrains Lifecycle ViewModel, Compose resources |
| Networking | Ktor Client (OkHttp / Darwin / JS engines), kotlinx.serialization, bearer auth with automatic token refresh |
| Maps | [MapCompose](https://github.com/p-lacroix/MapCompose) + OSM tiles (Android/Desktop), MapKit (iOS) |
| Payments | stripe-android, Stripe iOS (SPM), stripe-java |
| Server | Ktor Server (Netty, JWT auth, WebSockets), Exposed, Flyway, PostgreSQL, HikariCP, bcrypt |
| Build | Gradle version catalog, Kotlin 2.4, AGP 9, XcodeGen for the iOS project |

All versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Architecture

```mermaid
flowchart LR
  subgraph Clients
    A[androidApp<br/>MainActivity] --> C
    I[iosApp<br/>SwiftUI shell + XcodeGen] --> C
    W[web<br/>Kotlin/Wasm entry] --> C
    D[Desktop JVM<br/>DesktopMain.kt] --> C
  end
  C[composeApp<br/>shared Compose UI] --> S[shared<br/>models, DTOs, Ktor API client]
  S -- HTTPS / JSON --> SV[server<br/>Ktor + Exposed]
  SV --> DB[(PostgreSQL)]
  SV --> ST[Stripe]
```

Data flow inside the apps:

```mermaid
flowchart LR
  UI[Composable screen] -->|events| VM[ViewModel<br/>StateFlow]
  VM -->|state| UI
  VM --> R[Repository<br/>composeApp/.../data]
  R --> API[ChargeShareApi<br/>shared/.../api]
  API -->|Ktor Client| SRV[Ktor server<br/>/api/v1]
```

| Module | Content | Targets |
|---|---|---|
| `shared/` | Domain models, DTOs, Ktor API client (`ChargeShareApi`), tests in `commonTest` | JVM, Android, iOS, Wasm |
| `composeApp/` | Shared Compose UI, ViewModels, repositories, platform services (`expect`/`actual`) | Android, iOS, Desktop JVM, Wasm |
| `androidApp/` | Android application module | Android |
| `iosApp/` | SwiftUI host app + XcodeGen spec (`project.yml`) | iOS |
| `web/` | Compose for Web (Kotlin/Wasm) entry point | Wasm |
| `server/` | Ktor backend: REST API, JWT, Exposed, Flyway, Stripe, OCPP | JVM |
| `third_party/cmp-ios-chrome` | Optional git submodule ([cmp-ios-chrome](https://github.com/AMiketta/cmp-ios-chrome)), not required to build | – |

Android modules are only included when an Android SDK is found (`-Pchargeshare.android=false|true` overrides);
iOS targets are only declared on macOS (`-Pchargeshare.ios=true` forces klib compilation elsewhere).

## Quickstart

Requirements: **JDK 17+** (Gradle wrapper included). Optional: Android SDK, Docker, macOS + Xcode + XcodeGen.

```bash
git clone --recurse-submodules https://github.com/AMiketta/chargeshare.git
cd chargeshare
```

### 1. Start the backend

Easiest – embedded PostgreSQL, demo data and fake payments, no Docker needed:

```bash
./gradlew :server:runLocal        # server on http://localhost:8080, data in server/.local-pg
```

Or with Docker PostgreSQL:

```bash
docker compose up -d
DATABASE_URL=postgres://chargeshare:chargeshare@localhost:5432/chargeshare ./gradlew :server:run
curl localhost:8080/health        # {"status":"ok","database":"ok"}
```

In development mode (`ENV=development`, the default):

- **Dev seed** – sample data is created on start (8 wallboxes in Bremen and two demo accounts).
  Disable with `DEV_SEED=false`. Demo logins: `demo-driver@chargeshare.local` / `demo-host@chargeshare.local`, password `demo1234`.
- **Fake payment mode** – without `STRIPE_SECRET_KEY` the server uses a fake payment provider: bookings are confirmed
  immediately and no payment sheet is shown. All configuration options are listed in [`.env.example`](.env.example).

### 2. Run the apps

```bash
./gradlew :composeApp:run                       # Desktop (API URL via CHARGESHARE_API_URL, default http://localhost:8080)
./gradlew :web:wasmJsBrowserDevelopmentRun      # Web (API URL via ?api=https://…, default http://localhost:8080)
./gradlew :androidApp:installDebug              # Android; the emulator reaches the host at http://10.0.2.2:8080
#   other address: ./gradlew -Pchargeshare.apiUrl=http://192.168.0.10:8080 :androidApp:installDebug
```

iOS (macOS only):

```bash
brew install xcodegen
cd iosApp && xcodegen generate && open ChargeShare.xcodeproj
```

The Xcode project builds the Kotlin framework via `:composeApp:embedAndSignAppleFrameworkForXcode`.

### 3. Tests

```bash
./gradlew :shared:jvmTest :server:test :composeApp:desktopTest
```

## Learning topics – where to look

| Topic | Code |
|---|---|
| `expect`/`actual` platform services (secure token storage: Keystore/Keychain/…) | [`platform/SecureTokenStore.kt`](composeApp/src/commonMain/kotlin/app/chargeshare/platform/SecureTokenStore.kt) and its `*.android.kt`, `*.ios.kt`, `*.desktop.kt`, `*.wasmJs.kt` actuals; `TokenStore` interface in [`shared/.../api/TokenStore.kt`](shared/src/commonMain/kotlin/app/chargeshare/shared/api/TokenStore.kt) |
| Ktor client with bearer auth and automatic token refresh | [`shared/.../api/HttpClientFactory.kt`](shared/src/commonMain/kotlin/app/chargeshare/shared/api/HttpClientFactory.kt), [`ChargeShareApi.kt`](shared/src/commonMain/kotlin/app/chargeshare/shared/api/ChargeShareApi.kt) |
| ViewModels + repositories + manual DI | [`composeApp/.../presentation`](composeApp/src/commonMain/kotlin/app/chargeshare/presentation), [`data`](composeApp/src/commonMain/kotlin/app/chargeshare/data), [`di/AppContainer.kt`](composeApp/src/commonMain/kotlin/app/chargeshare/di/AppContainer.kt) |
| Stripe on Android / iOS (Google Pay, Apple Pay) | [`payment/PaymentLauncher.kt`](composeApp/src/commonMain/kotlin/app/chargeshare/payment/PaymentLauncher.kt), [`androidApp/.../StripePaymentLauncher.kt`](androidApp/src/main/kotlin/app/chargeshare/android/StripePaymentLauncher.kt), [`iosMain/.../IosPaymentBridge.kt`](composeApp/src/iosMain/kotlin/app/chargeshare/payment/IosPaymentBridge.kt) + [`StripePaymentBridge.swift`](iosApp/ChargeShare/StripePaymentBridge.swift) |
| Server-side payments with a fake provider for dev | [`server/.../payments/PaymentProvider.kt`](server/src/main/kotlin/app/chargeshare/server/payments/PaymentProvider.kt) |
| Custom source set shared by some targets (MapCompose for Android + Desktop) | `mapcomposeMain` in [`composeApp/build.gradle.kts`](composeApp/build.gradle.kts), [`WallboxMap.mapcompose.kt`](composeApp/src/mapcomposeMain/kotlin/app/chargeshare/ui/map/WallboxMap.mapcompose.kt), MapKit in [`WallboxMap.ios.kt`](composeApp/src/iosMain/kotlin/app/chargeshare/ui/map/WallboxMap.ios.kt) |
| Kotlin/Wasm web target | [`web/.../Main.kt`](web/src/wasmJsMain/kotlin/app/chargeshare/web/Main.kt), [`composeApp/src/wasmJsMain`](composeApp/src/wasmJsMain) |
| iOS project generated with XcodeGen | [`iosApp/project.yml`](iosApp/project.yml) |
| Ktor server: JWT, Exposed, Flyway migrations, embedded Postgres for local dev | [`server/`](server/src/main/kotlin/app/chargeshare/server), [`db/migration`](server/src/main/resources/db/migration), `runLocal` task in [`server/build.gradle.kts`](server/build.gradle.kts) |

## Screenshots

### Android

| Onboarding | Search & map | Wallbox detail |
|---|---|---|
| <img src=".github/screenshots/android-onboarding.png" width="240"> | <img src=".github/screenshots/android-search-map.png" width="240"> | <img src=".github/screenshots/android-wallbox-detail.png" width="240"> |
| **Booking** | **Host dashboard** | **Profile** |
| <img src=".github/screenshots/android-booking.png" width="240"> | <img src=".github/screenshots/android-host-dashboard.png" width="240"> | <img src=".github/screenshots/android-profile.png" width="240"> |

### Web (Compose for Web / Kotlin Wasm)

| Search (list view) | Login |
|---|---|
| <img src=".github/screenshots/web-search-list.png" width="400"> | <img src=".github/screenshots/web-login.png" width="400"> |

### Website (landing page)

<img src=".github/screenshots/website-landing.png" width="600">

### iOS

_Coming soon._

All screenshots show seeded demo data (`DEV_SEED`).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Support

If this project helped you learn something, you can [buy me a coffee via PayPal](https://paypal.me/Miketta). ☕

## License

Licensed under the [Apache License 2.0](LICENSE) – Copyright 2026 Andreas Miketta.
**The name "ChargeShare" and its logo are excluded from the license** – please use your own name and logo for forks
and derived apps (see [NOTICE](NOTICE)).
