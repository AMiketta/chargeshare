# ChargeShare

Peer-to-Peer Wallbox-Sharing ("Airbnb für Wallboxen"), komplett in **Kotlin Multiplatform**.
Primärfarbe `#0B6E4F`, Akzent `#F0A500`.

> **Für Entwickler und KI-Agenten:** Projektwissen, Architektur und verbindliche Kotlin-Standards stehen in [`AGENTS.md`](AGENTS.md).

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

### Lokal ohne Docker (Entwicklung)

```bash
./gradlew :server:runLocal      # eingebettetes PostgreSQL (Daten in server/.local-pg) + Server auf 0.0.0.0:8080
```

Im Entwicklungsmodus (`ENV=development`, Standard) gilt:

- **Dev-Seed**: beim Start werden *Beispieldaten* angelegt (idempotent): 8 Wallboxen in Bremen (Name beginnt mit `[Beispiel]`)
  sowie zwei Demo-Logins `demo-host@chargeshare.local` / `demo-driver@chargeshare.local`, Passwort `demo1234`.
  Abschalten mit `DEV_SEED=false`; in Produktion ist der Seed verboten.
- **Fake-Zahlung**: ohne `STRIPE_SECRET_KEY` nutzt der Server einen Fake-Payment-Provider (kein Netzwerk). Buchungen sind sofort
  `confirmed`/bezahlt, `client_secret` ist ein Platzhalter, Erstattungen sind No-ops. In Produktion ist `STRIPE_SECRET_KEY` Pflicht.
- Android-Emulator erreicht den Host unter `http://10.0.2.2:8080`.

### API-Stand

Alle Pfade, JSON-Feldnamen und das Fehlerformat (`{"error": "...", "code": "VALIDATION_ERROR"}`) folgen dem Go-Backend.

| Bereich | Status |
|---|---|
| Auth (`register`, `login`, `refresh`, `logout`, `users/me` GET/PUT/DELETE, `users/me/role`) | implementiert: bcrypt (Kosten 12), Access-JWT (15 min, HS256), Refresh-Token zufaellig + als SHA-256 in `refresh_tokens`, Rotation bei jedem Refresh (Einmalverwendung) |
| `POST /auth/apple` | implementiert, aber **nur mit `APPLE_BUNDLE_ID`** (prueft Signatur gegen Apples JWKS, `iss`, `aud`, `exp`); ohne Konfiguration `501`. Nicht gegen echte Apple-Tokens getestet (Test mit lokal signiertem Token) |
| Wallboxen (CRUD, Suche per `earth_distance`, Status, Wochenplan, Slots, Reviews-Liste) | implementiert |
| Buchungen (anlegen, Liste, Detail, stornieren, `confirm-payment`) | implementiert; Ueberlappungsschutz per Exclusion-Constraint + Advisory-Lock |
| Host (`/host/bookings`, `/host/earnings`, `/host/sessions`), `POST /reviews` | implementiert |
| Zahlung | `PaymentProvider`-Interface: `FakePaymentProvider` (Dev) oder `StripePaymentProvider` (PaymentIntent mit Connect-Transfer + Plattformgebuehr, Refund, Connect-Onboarding, Webhook mit Signaturpruefung). **Stripe ist nicht gegen echte Stripe-Konten getestet** (nur Webhook-Signatur) |
| OCPP 1.6J `GET /ocpp/{id}` (WebSocket) | implementiert: BootNotification, Heartbeat, Authorize, Start-/StopTransaction, MeterValues (nur Log), StatusNotification. RemoteStart/Stop (im Go-Code ungenutzt) nicht portiert |
| Rate-Limiting (Go: `ratelimit.go`) | **nicht portiert** |

Bewusste Abweichungen vom Go-Code (Fehler/Luecken dort): Stornieren liefert `204` (Client laedt die Buchung danach neu); Booking-Insert vor
Payment-Intent; Exclusion-Verletzung -> `409` statt `500`; `confirm-payment` prueft den Zahlungsstatus beim Provider (`402`);
Konto loeschen entfernt abhaengige Buchungen/Reviews; E-Mail wird kleingeschrieben; Rolle wird aus der DB statt aus dem Token gelesen;
Slots in der Vergangenheit werden nicht angeboten; unbekannte OCPP-Ladepunkte (`ocpp_id` an keiner Wallbox) werden abgelehnt.

## Versionen (Stand Oktober 2026)

Kotlin 2.4.20 · Gradle 9.8.0 · AGP 9.4.1 · Compose Multiplatform 1.12.1 (Material3 `1.12.0-alpha03`, für CMP 1.12 gibt es kein stabiles M3) ·
Ktor 3.6.0 · kotlinx.serialization 1.11.0 · Coroutines 1.11.0 · Exposed 1.5.0 · Flyway 13.9.0 · PostgreSQL-Treiber 42.7.13.
Alle Versionen stehen in `gradle/libs.versions.toml`.

## Nächste Schritte

1. Server: Rate-Limiting, Stripe gegen Testkonto pruefen, Zahlung nach OCPP-Abschluss auf tatsaechliche kWh abrechnen, RemoteStart/Stop
2. composeApp: Login, Karte (native Map-Anbindung per `expect/actual`), Detail, Buchung, Host-Dashboard, Profil
3. Token-Speicherung pro Plattform (Keychain / EncryptedSharedPreferences / localStorage) hinter `TokenStore`
4. CI (GitHub Actions: Linux für shared/server/web/android, macOS für iOS)
