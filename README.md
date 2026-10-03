# ChargeShare

Peer-to-Peer Wallbox-Sharing ("Airbnb für Wallboxen"), komplett in **Kotlin Multiplatform**.
Primärfarbe `#0B6E4F`, Akzent `#F0A500`.

> **Für Entwickler und KI-Agenten:** Projektwissen, Architektur und verbindliche Kotlin-Standards stehen in [`AGENTS.md`](AGENTS.md).

## Module

| Modul | Inhalt | Targets |
|---|---|---|
| `shared/` | Domain-Modelle, DTOs (JSON-Feldnamen wie im Go-Backend), Ktor-API-Client (`ChargeShareApi`, Bearer-Auth mit Auto-Refresh), Tests in `commonTest` | JVM, Android\*, iOS\*\*, Wasm |
| `composeApp/` | Gemeinsame Compose-Multiplatform-UI: Login/Registrierung, Suche, Wallbox-Detail, Buchungsflow, Buchungen, Host-Dashboard, Profil; ViewModels, Repositories, `TokenStore` je Plattform | Android\*, iOS\*\*, Desktop-JVM, Wasm |
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

### App gegen das lokale Backend

```bash
./gradlew :server:runLocal                 # Backend + Demo-Daten (Terminal 1)
./gradlew :composeApp:run                  # Desktop-App, Adresse per CHARGESHARE_API_URL (Standard http://localhost:8080)
./gradlew :androidApp:installDebug         # Android; Emulator erreicht den Host unter http://10.0.2.2:8080
# andere Adresse (z. B. echtes Gerät im WLAN): ./gradlew -Pchargeshare.apiUrl=http://192.168.0.10:8080 :androidApp:installDebug
```

Demo-Logins: `demo-driver@chargeshare.local` / `demo-host@chargeshare.local`, Passwort `demo1234`.
Die Web-App liest die API-Adresse aus `?api=https://…` (Standard `http://localhost:8080`).
Die Karte ist noch ein Platzhalter (Liste statt Map); siehe `AGENTS.md`, Abschnitt „Bekannte Lücken“.

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
| `GET /payments/config` | implementiert: öffentliche Zahlungskonfiguration (siehe Abschnitt „Zahlung“); nur mit Login |
| Host (`/host/bookings`, `/host/earnings`, `/host/sessions`), `POST /reviews` | implementiert |
| Zahlung | `PaymentProvider`-Interface: `FakePaymentProvider` (Dev) oder `StripePaymentProvider` (PaymentIntent mit Connect-Transfer + Plattformgebuehr, Refund, Connect-Onboarding, Webhook mit Signaturpruefung). **Stripe ist nicht gegen echte Stripe-Konten getestet** (nur Webhook-Signatur) |
| OCPP 1.6J `GET /ocpp/{id}` (WebSocket) | implementiert: BootNotification, Heartbeat, Authorize, Start-/StopTransaction, MeterValues (nur Log), StatusNotification. RemoteStart/Stop (im Go-Code ungenutzt) nicht portiert |
| Rate-Limiting (Go: `ratelimit.go`) | **nicht portiert** |

Bewusste Abweichungen vom Go-Code (Fehler/Luecken dort): Stornieren liefert `204` (Client laedt die Buchung danach neu); Booking-Insert vor
Payment-Intent; Exclusion-Verletzung -> `409` statt `500`; `confirm-payment` prueft den Zahlungsstatus beim Provider (`402`);
Konto loeschen entfernt abhaengige Buchungen/Reviews; E-Mail wird kleingeschrieben; Rolle wird aus der DB statt aus dem Token gelesen;
Slots in der Vergangenheit werden nicht angeboten; unbekannte OCPP-Ladepunkte (`ocpp_id` an keiner Wallbox) werden abgelehnt.

## Zahlung (Stripe, Google Pay, Apple Pay)

Die Zahlung läuft über das **Stripe PaymentSheet**. Google Pay (Android) und Apple Pay (iOS) sind darin Wallet-Schaltflächen
für die Zahlungsart „card“; die Fahrerin wählt im Buchungsflow zwischen *Wallet* und *Karte und weitere*.

**Ablauf (echte Zahlung, `STRIPE_SECRET_KEY` gesetzt):**

1. App lädt `GET /api/v1/payments/config` (Publishable Key, Händlername/-land, Währung, Google-Pay-Umgebung, Apple-Merchant-ID – **keine Secrets**).
2. `POST /api/v1/bookings` legt die Buchung als `pending` an und liefert das `client_secret` des PaymentIntents
   (`automatic_payment_methods` aktiv, `allow_redirects=never`: Karte und Wallets, keine Weiterleitungs-Methoden).
3. Die App zeigt das PaymentSheet (Android: `StripePaymentLauncher`, iOS: Swift-Brücke `StripePaymentBridge`).
4. Bei Erfolg ruft die App `POST /api/v1/bookings/{id}/confirm-payment`; der Server prüft den PaymentIntent bei Stripe (`402`, wenn nicht bezahlt).
   Der Webhook (`payment_intent.succeeded`) bestätigt die Buchung unabhängig davon.
5. Abbruch/Fehler: die Buchung bleibt `pending`; die App bietet „Erneut bezahlen“ oder „Buchung verwerfen“ (Storno gibt den Slot frei).

**Dev-Modus (ohne `STRIPE_SECRET_KEY`):** `GET /payments/config` meldet `provider: "fake"`; Buchungen sind sofort `confirmed`, die App zeigt
nur den Hinweis „Testzahlung“ und öffnet kein Zahlungsfenster. Das gilt auch für Desktop und Web.

**Plattformen:** Android = Google Pay + Karte · iOS = Apple Pay + Karte (nur mit Merchant-ID) · **Desktop/Web: keine Wallets** –
mit aktivem Stripe zeigt die Buchungsmaske den Hinweis, dass nur in der Android-/iOS-App bezahlt werden kann.

### Konfiguration (Umgebungsvariablen des Servers, siehe `.env.example`)

| Variable | Bedeutung |
|---|---|
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | Secrets, nur auf dem Server. Ohne Secret Key: Fake-Zahlung (nur Dev) |
| `STRIPE_PUBLISHABLE_KEY` | `pk_test_…` / `pk_live_…`, wird an die Apps ausgeliefert. Muss mit `pk_` beginnen (ein `sk_…` wird abgelehnt). In Produktion Pflicht, sobald Stripe aktiv ist |
| `APPLE_PAY_MERCHANT_ID` | z. B. `merchant.app.chargeshare`; ohne sie wird Apple Pay in der App nicht angeboten (Karte bleibt) |
| `GOOGLE_PAY_ENVIRONMENT` | `test` oder `production`; Standard: aus dem Publishable Key abgeleitet (`pk_live_` ⇒ `production`) |
| `MERCHANT_DISPLAY_NAME`, `MERCHANT_COUNTRY_CODE` | Anzeigename (Standard `ChargeShare`) und ISO-Land (Standard `DE`) in den Zahlungsdialogen; Währung ist fest `eur` |

Keine Schlüssel ins Repo: alles per Umgebung/`.env` (steht in `.gitignore`).

### Google Pay (Android)

- SDK: `com.stripe:stripe-android:23.21.0` (Version Catalog), Manifest-Eintrag `com.google.android.gms.wallet.api.enabled`.
- **Test vs. Produktion:** `GOOGLE_PAY_ENVIRONMENT=test` (Standard bei `pk_test_`) nutzt Googles Testumgebung mit Stripe-Testkarten.
  Getestet werden sollte auf einem **echten Gerät** mit Google-Konto und einer Karte im Google Wallet; im Emulator erscheint meist nur die Karteneingabe.
- **Produktion:** Google verlangt ein Google-Business-Profil und die Freischaltung in der [Google Pay & Wallet Console](https://pay.google.com/business/console)
  (Integration „Android“ mit Screenshots der App und dem Signatur-Zertifikat der Release-Version). Danach `GOOGLE_PAY_ENVIRONMENT=production` mit `pk_live_…`.

### Apple Pay (iOS) – nur auf einem Mac fertigzustellen

Der Kotlin-Teil (`IosPaymentLauncher`, `IosPaymentBridge`) kompiliert (`./gradlew -Pchargeshare.ios=true :composeApp:compileKotlinIosArm64`).
**Nicht getestet** und auf Linux nicht kompilierbar sind die Swift-Datei `iosApp/ChargeShare/StripePaymentBridge.swift`, die SPM-Abhängigkeit
(`stripe-ios-spm` ab 26.12.1, Produkt `StripePaymentSheet`) und die Entitlements in `iosApp/project.yml`. Zu tun:

1. Bezahltes **Apple-Developer-Konto** (Apple Developer Program). Mit einem kostenlosen Konto lässt sich die Apple-Pay-Capability nicht signieren.
2. Im Developer-Portal eine **Merchant ID** anlegen (z. B. `merchant.app.chargeshare`); in `project.yml` (`com.apple.developer.in-app-payments`) und in
   `APPLE_PAY_MERCHANT_ID` des Servers identisch eintragen. Bundle-ID `app.chargeshare` und `DEVELOPMENT_TEAM` setzen.
3. Im Stripe-Dashboard unter *Einstellungen → Zahlungsmethoden → Apple Pay* ein **Payment Processing Certificate** für diese Merchant ID erstellen (CSR bei Stripe laden,
   Zertifikat bei Apple erzeugen, wieder zu Stripe hochladen) und in Xcode die Capability *Apple Pay* mit der Merchant ID aktivieren (xcodegen setzt die Entitlement-Datei).
4. `brew install xcodegen && cd iosApp && xcodegen generate`, Xcode öffnen, bauen, Swift-Datei und Zusammenspiel mit Kotlin prüfen (Compiler-Fehler in der Brücke sind möglich).
5. Apple Pay braucht ein Gerät oder einen Simulator mit einer Testkarte in der Wallet-App; Server-Adresse in `ContentView.swift` für echte Geräte anpassen (HTTPS).

## Versionen (Stand Oktober 2026)

Kotlin 2.4.20 · Gradle 9.8.0 · AGP 9.4.1 · Compose Multiplatform 1.12.1 (Material3 `1.12.0-alpha03`, für CMP 1.12 gibt es kein stabiles M3) ·
Stripe Android SDK 23.21.0 · Stripe iOS SDK 26.12.1 (SPM) · Stripe Java 34.0.0 ·
Ktor 3.6.0 · kotlinx.serialization 1.11.0 · Coroutines 1.11.0 · Exposed 1.5.0 · Flyway 13.9.0 · PostgreSQL-Treiber 42.7.13.
Alle Versionen stehen in `gradle/libs.versions.toml`.

## Nächste Schritte

1. Server: Rate-Limiting, Stripe gegen Testkonto pruefen (Google Pay/Apple Pay mit echten Testschlüsseln durchspielen), Zahlung nach OCPP-Abschluss auf tatsaechliche kWh abrechnen, RemoteStart/Stop
2. composeApp: echte Karte (native Map-Anbindung per `expect/actual`), Standort mit Einwilligung, Bewertungen schreiben, Wallbox bearbeiten, „Jetzt bezahlen“ für offene Buchungen in der Buchungsliste
3. iOS auf einem Mac bauen und testen (Keychain-`TokenStore` ist ungetestet)
4. CI (GitHub Actions: Linux für shared/server/web/android, macOS für iOS)
