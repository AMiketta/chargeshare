# ChargeShare

Peer-to-Peer Wallbox-Sharing ("Airbnb für Wallboxen"), komplett in **Kotlin Multiplatform**.
Primärfarbe `#0B6E4F`, Akzent `#F0A500`.

## Module

| Modul | Inhalt | Targets |
|---|---|---|
| `shared/` | Domain-Modelle, DTOs (JSON-Feldnamen wie im Go-Backend), Ktor-API-Client (`ChargeShareApi`, Bearer-Auth mit Auto-Refresh), Tests in `commonTest` | JVM, Android\*, iOS\*\*, Wasm |
| `composeApp/` | Gemeinsame Compose-Multiplatform-UI (`App()`, Theme mit Markenfarben) | Android\*, iOS\*\*, Desktop-JVM, Wasm |
| `androidApp/` | Android-Application-Modul (`MainActivity` -> `App()`) | Android\* |
| `iosApp/` | SwiftUI-Hülle + XcodeGen-Spec (`project.yml`) | iOS\*\* |
| `server/` | Ktor-Backend: Netty, JWT, Exposed, Flyway, PostgreSQL, kotlinx.serialization | JVM |
| `web/` | Compose-Web (Kotlin/Wasm) Einstiegspunkt, nutzt `composeApp` | Wasm |
| `docs/` | Konzept (PDF) und HTML-Design | – |
| `legacy/` | Referenz: altes Go-Backend und SwiftUI-App (wird nach der Migration entfernt) | – |

\* Android-Module werden nur eingebunden, wenn ein Android SDK gefunden wird (siehe unten).
\*\* iOS-Targets werden nur auf macOS deklariert (Kotlin/Native-Frameworks bauen nur dort).

## Voraussetzungen

- **JDK 17+** (getestet mit Temurin 17.0.20)
- Gradle wird über den Wrapper bereitgestellt (`./gradlew`, Gradle 9.8.0)
- Optional: Android SDK (Platform 37, Build-Tools 36) via `ANDROID_HOME` oder `local.properties` (`sdk.dir=...`)
- Optional (nur macOS): Xcode + [XcodeGen](https://github.com/yonaskolb/XcodeGen) für die iOS-App
- Optional: Docker für eine lokale PostgreSQL-Datenbank

## Build & Test

```bash
# Kern (läuft auf Linux/macOS/Windows, ohne Android SDK und ohne Datenbank)
./gradlew :shared:jvmTest :server:build

# Web (Compose/Wasm) – lädt beim ersten Mal Node.js über Gradle
./gradlew :web:wasmJsBrowserDistribution     # Ausgabe: web/build/dist/wasmJs/productionExecutable
./gradlew :web:wasmJsBrowserDevelopmentRun   # Dev-Server mit Live-Reload

# Desktop-Vorschau der gemeinsamen UI
./gradlew :composeApp:run

# Android (mit SDK)
./gradlew :androidApp:assembleDebug
```

### Android-Target an/aus

`settings.gradle.kts` erkennt das SDK automatisch. Überschreiben per Gradle-Property:

```bash
./gradlew -Pchargeshare.android=false build   # Android komplett ausblenden
./gradlew -Pchargeshare.android=true build    # erzwingen (SDK muss vorhanden sein)
```

### iOS (macOS)

iOS-Targets (`iosArm64`, `iosSimulatorArm64`) sind nur auf macOS aktiv; auf anderen Hosts erzwingbar mit
`-Pchargeshare.ios=true` (nur Klib-Kompilierung, kein Framework-Link).

```bash
brew install xcodegen
cd iosApp && xcodegen generate && open ChargeShare.xcodeproj
```

Das Xcode-Projekt baut das Kotlin-Framework `ComposeApp` per Gradle-Run-Script
(`:composeApp:embedAndSignAppleFrameworkForXcode`).

## Server starten

```bash
# optional: lokale Datenbank
docker compose up -d
export DATABASE_URL=postgres://chargeshare:chargeshare@localhost:5432/chargeshare

./gradlew :server:run
curl localhost:8080/health      # {"status":"ok","database":"ok"}
```

Ohne `DATABASE_URL` läuft der Server ohne Datenbank (`"database":"disabled"`). Beim Start mit Datenbank wendet
Flyway `server/src/main/resources/db/migration/V1__init.sql` an (1:1 aus `legacy/go-backend/.../000001_init.up.sql`;
braucht die PostgreSQL-Extensions `pgcrypto`, `cube`, `earthdistance`, `btree_gist` – im offiziellen `postgres`-Image enthalten).
Konfiguration über Umgebungsvariablen, siehe `.env.example` (gleiche Namen wie im Go-Backend).

### API-Stand

| Status | Routen |
|---|---|
| implementiert | `GET /health` |
| Stub (Validierung + `501 NOT_IMPLEMENTED`) | `POST /api/v1/auth/register`, `/login`, `/apple`, `/refresh`, `POST /auth/logout` (JWT), `GET /api/v1/users/me` (JWT) |
| offen | übrige Routen aus `legacy/go-backend/README.md` (Wallboxen, Buchungen, Reviews, Host, Payments/Stripe, OCPP-WebSocket) – der Client in `shared` kennt sie bereits |

Fehlerformat wie im Go-Backend: `{"error": "...", "code": "VALIDATION_ERROR"}`.

## Versionen (Stand Oktober 2026)

Kotlin 2.4.20 · Gradle 9.8.0 · AGP 9.4.1 · Compose Multiplatform 1.12.1 (Material3 `1.12.0-alpha03`, für CMP 1.12 gibt es kein stabiles M3) ·
Ktor 3.6.0 · kotlinx.serialization 1.11.0 · Coroutines 1.11.0 · Exposed 1.5.0 · Flyway 13.9.0 · PostgreSQL-Treiber 42.7.13.
Alle Versionen stehen in `gradle/libs.versions.toml`.

## Nächste Schritte

1. Server: Auth-Logik (Argon2/bcrypt, Refresh-Rotation, Sign in with Apple), dann Wallboxen/Buchungen/Reviews/Host, Stripe Connect, OCPP 1.6
2. composeApp: Login, Karte (native Map-Anbindung per `expect/actual`), Detail, Buchung, Host-Dashboard, Profil
3. Token-Speicherung pro Plattform (Keychain / EncryptedSharedPreferences / localStorage) hinter `TokenStore`
4. CI (GitHub Actions: Linux für shared/server/web/android, macOS für iOS)
