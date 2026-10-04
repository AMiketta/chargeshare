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
Die Suche zeigt auf Android und Desktop eine Karte (siehe [Karte](#karte)), auf iOS und im Web die Liste.

## Server starten

```bash
# optional: lokale Datenbank
docker compose up -d
export DATABASE_URL=postgres://chargeshare:chargeshare@localhost:5432/chargeshare

./gradlew :server:run
curl localhost:8080/health      # {"status":"ok","database":"ok"}
```

Ohne `DATABASE_URL` läuft der Server ohne Datenbank (`"database":"disabled"`). Beim Start mit Datenbank wendet
Flyway `server/src/main/resources/db/migration/V1__init.sql` (1:1 aus `legacy/go-backend/.../000001_init.up.sql`) und `V2__wallbox_description.sql` (Beschreibung der Wallbox) an;
V1 braucht die PostgreSQL-Extensions `pgcrypto`, `cube`, `earthdistance`, `btree_gist` – im offiziellen `postgres`-Image enthalten.
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
| Auth (`register`, `login`, `refresh`, `logout`, `users/me` GET/PUT/DELETE, `users/me/role`, `users/me/stats`) | implementiert (`PUT /users/me`: Name 1–100 Zeichen, getrimmt; Telefon optional, 6–20 Zeichen aus Ziffern/`+`/Leerzeichen/`-`/`/`/Klammern, leer = entfernen; Profil-Dialog in der App): bcrypt (Kosten 12), Access-JWT (15 min, HS256), Refresh-Token zufaellig + als SHA-256 in `refresh_tokens`, Rotation bei jedem Refresh (Einmalverwendung). `GET /users/me/stats` liefert die Profil-Kennzahlen: abgeschlossene Buchungen als Fahrer (`sessions`), Summe `actual_kwh` (`kwh_charged`, 1 Nachkommastelle) und Bewertungsdurchschnitt/-anzahl der eigenen Wallboxen (`rating_avg` ist `null` ohne Bewertungen) |
| `POST /auth/apple` | implementiert, aber **nur mit `APPLE_BUNDLE_ID`** (prueft Signatur gegen Apples JWKS, `iss`, `aud`, `exp`); ohne Konfiguration `501`. Nicht gegen echte Apple-Tokens getestet (Test mit lokal signiertem Token) |
| Wallboxen (CRUD, Suche per `earth_distance`, Status, Wochenplan, Slots, Reviews-Liste) | implementiert |
| Buchungen (anlegen, Liste, Detail, stornieren, `confirm-payment`, `pay`) | implementiert; Ueberlappungsschutz per Exclusion-Constraint + Advisory-Lock |
| `GET /payments/config` | implementiert: öffentliche Zahlungskonfiguration (siehe Abschnitt „Zahlung“); nur mit Login |
| Host (`/host/bookings`, `/host/earnings`, `/host/sessions`), `POST /reviews` | implementiert; Bewertung nur durch den Fahrer einer `completed`-Buchung, einmal pro Buchung (`409`), 1–5 Sterne, Kommentar ≤ 2000 Zeichen (leer = kein Kommentar); `Booking.reviewed` zeigt, ob schon bewertet wurde; Durchschnitt/Anzahl werden an der Wallbox exakt neu berechnet |
| Zahlung | `PaymentProvider`-Interface: `FakePaymentProvider` (Dev) oder `StripePaymentProvider` (PaymentIntent mit Connect-Transfer + Plattformgebuehr, Refund, Connect-Onboarding, Webhook mit Signaturpruefung). **Stripe ist nicht gegen echte Stripe-Konten getestet** (nur Webhook-Signatur) |
| OCPP 1.6J `GET /ocpp/{id}` (WebSocket) | implementiert: BootNotification, Heartbeat, Authorize, Start-/StopTransaction, MeterValues (nur Log), StatusNotification. RemoteStart/Stop (im Go-Code ungenutzt) nicht portiert |
| Rate-Limiting (Go: `ratelimit.go`) | portiert: `POST /auth/register\|login\|apple` max. 10 Versuche/Minute je Client-Adresse (`AUTH_RATE_LIMIT_*`), danach `429` mit `Retry-After`; im Speicher je Instanz; Refresh und übrige Routen ungebremst |

Bewusste Abweichungen vom Go-Code (Fehler/Luecken dort): Stornieren liefert `204` (Client laedt die Buchung danach neu); Booking-Insert vor
Payment-Intent; Exclusion-Verletzung -> `409` statt `500`; `confirm-payment` prueft den Zahlungsstatus beim Provider (`402`);
Konto loeschen entfernt abhaengige Buchungen/Reviews; E-Mail wird kleingeschrieben; Rolle wird aus der DB statt aus dem Token gelesen;
Slots in der Vergangenheit werden nicht angeboten; belegte Slots der Zukunft stehen zusätzlich in `locked` (Antwort von `GET /wallboxes/{id}/slots`, für die ausgegraute Darstellung im Buchungsraster); unbekannte OCPP-Ladepunkte (`ocpp_id` an keiner Wallbox) werden abgelehnt.

## Karte

Die Suche hat einen Umschalter **Karte / Liste**; auf Android und Desktop ist die Karte die Startansicht.
Jede Wallbox ist ein Pin (grün = verfügbar, grau = offline); ein Tipp auf den Pin öffnet die Detailseite.
Die Kamera startet auf Bremen und springt auf den Umfang der Suchergebnisse. Über „Filter“ lassen sich die Suchfilter über der Karte einblenden.

- **Bibliothek:** [MapCompose-MP](https://github.com/p-lr/MapComposeMP) (`ovh.plrapps:mapcompose-mp`, Apache-2.0) – reines Compose Multiplatform (Android, Desktop, iOS, Wasm),
  Marker/Gesten/Pinch-Zoom, **kein API-Key**, keine native SDK-Abhängigkeit. Kacheln liefert `OsmTileSource` von `tile.openstreetmap.org` (Quellenangabe „© OpenStreetMap-Mitwirkende“ steht auf der Karte).
  Verworfen: MapLibre Compose (kein Wasm-Target, Desktop „early“, braucht zusätzlich einen Vektorstil-Anbieter), `Compose-Multiplatform-Tile-Map` (0.x, kein Marker-API, Pins müssten selbst gezeichnet werden).
- **Plattformen:** `expect val isMapSupported` (`platform/MapSupport.kt`): Android und Desktop = `true`; **iOS und Web = `false`** – dort zeigt die Suche die Liste mit Hinweis, kein Umschalter.
  Die Bibliothek läuft grundsätzlich auch auf iOS/Wasm; aktiviert wird sie dort erst, wenn sie auf einem Mac bzw. im Browser geprüft ist (nur `actual` auf `true` setzen).
- **Ohne Internet** (oder wenn OSM Kacheln ablehnt) bleibt die Kartenfläche leer, Pins und Navigation funktionieren weiter.
- **Produktion:** die OSM-Server sind nur für moderate Nutzung gedacht ([Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/)). Vor einem Store-Release einen eigenen Kachelserver oder einen
  Anbieter mit Vertrag verwenden (`OsmTileSource(baseUrl = …)`).
- **Code:** `presentation/map/` (Web-Mercator-Projektion, `Wallbox.toPin()`, Kartenausschnitt – getestet), `data/OsmTileSource.kt` (Kachelabruf mit Cache, getestet), `ui/map/WallboxMap.kt` (Composable).

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
   Beim Storno einer unbezahlten Buchung schließt der Server außerdem den offenen PaymentIntent bei Stripe (`cancelIntent`; fehlertolerant –
   ein bereits bezahlter oder stornierter Intent blockiert den Storno nicht). Bezahlte Buchungen werden wie bisher per Refund erstattet.
6. **„Jetzt bezahlen“ (`POST /api/v1/bookings/{id}/pay`):** In der Buchungsliste zeigt jede offene (`pending`, noch nicht beendete) Buchung auf Android/iOS den Button
   „Jetzt bezahlen“. Der Server liefert das `client_secret` des vorhandenen PaymentIntents (derselbe Betrag); ist der Intent storniert oder fehlt er, wird ein
   neuer angelegt. Ist er inzwischen bezahlt, wird die Buchung bestätigt (ohne `client_secret`). Nur der Fahrer darf bezahlen (`403`), nur `pending` (`400`).
7. **Automatischer Ablauf:** Ein Hintergrund-Job (`PendingBookingSweeper`) storniert `pending`-Buchungen, die älter als `PENDING_BOOKING_TTL_MINUTES` (Standard 15, `0` = aus)
   sind, im Abstand von `PENDING_BOOKING_SWEEP_SECONDS` (Standard 60). `cancel_reason` ist dann `payment_expired`, der Slot wird frei. Ablauf pro Buchung: zuerst
   `cancelIntent` (Fehler werden nur geloggt), danach Zahlungsstatus prüfen – war inzwischen bezahlt, wird die Buchung **bestätigt statt storniert**;
   lässt sich der Status nicht abfragen (Stripe nicht erreichbar), bleibt die Buchung unverändert und wird im nächsten Durchlauf erneut geprüft. „Jetzt bezahlen“ verlängert die Frist nicht.

**Auszahlungs-Onboarding (`POST /payments/onboard`):** Stripe-Fehler werden nicht als `500` durchgereicht, sondern mit lesbarer deutscher Meldung
(ohne Stripe-Rohtext oder Schlüssel) beantwortet: `503`, wenn Stripe Connect im Konto nicht aktiviert (Dashboard → Connect, im Testmodus ohne
Firmendaten möglich) oder Stripe nicht erreichbar ist; `502` bei falschem API-Schlüssel oder sonstiger Ablehnung. Code ist jeweils `INTERNAL_ERROR`.

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
| `PENDING_BOOKING_TTL_MINUTES`, `PENDING_BOOKING_SWEEP_SECONDS` | Frist (Standard 15 Minuten, `0` = kein automatischer Ablauf) und Prüfintervall (Standard 60 s) für unbezahlte Buchungen |
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

## CI und Self-hosted-Runner

CI läuft in GitHub Actions (`.github/workflows/ci.yml`) und baut Tests, Server, Desktop-Tests, Web und Android-Debug-APK. Der Job läuft standardmäßig auf einem **Self-hosted-Runner** (Laptop, Name `bazzite-laptop`, Label `chargeshare`), damit keine GitHub-Build-Minuten verbraucht werden. **Ist der Laptop aus oder offline, bleibt die CI in der Warteschlange.**

- **Betrieb:** Runner liegt in `~/actions-runner` und läuft als systemd-User-Dienst `github-runner.service`.
  Status: `systemctl --user status github-runner` · Log: `journalctl --user -u github-runner -f` ·
  Neustart: `systemctl --user restart github-runner` · Stopp/Start: `systemctl --user stop|start github-runner`.
  Damit der Dienst auch ohne Anmeldung startet, einmal im normalen Terminal `loginctl enable-linger $USER` ausführen.
- **Ressourcen:** Der Dienst läuft mit `Nice=15`, `MemoryHigh=5G`, `MemoryMax=6G` (Laptop hat 16 GB und nebenher Emulator/IDE); der CI-Job begrenzt Gradle auf `-Xmx3g`, den Kotlin-Daemon auf `-Xmx2g` (mit 1g ging dem Wasm-Compile der Speicher aus) und `--max-workers=2`.
- **Umgebung:** `~/actions-runner/.env` setzt `ANDROID_HOME`/`ANDROID_SDK_ROOT` (`~/Android/Sdk`); das JDK 17 holt `actions/setup-java` selbst. Gradle-Cache (`~/.gradle`) bleibt zwischen Läufen erhalten.
- **Zurück auf GitHub-gehostet (ohne Code-Änderung):** `gh variable set CI_RUNS_ON --body '"ubuntu-latest"'` – wieder auf den Laptop: `gh variable delete CI_RUNS_ON`.
- **Runner entfernen:** `systemctl --user disable --now github-runner`, dann in `~/actions-runner`:
  `./config.sh remove --token <Token>` (Token: `gh api -X POST repos/AMiketta/chargeshare/actions/runners/remove-token -q .token`), danach `rm -rf ~/actions-runner ~/.config/systemd/user/github-runner.service`.
- **Sicherheit:** Self-hosted-Runner führen den Code der PRs auf dem Laptop aus. Nur für dieses **private** Repo ohne fremde Mitwirkende verwenden, nie für öffentliche Forks.

## Dunkelmodus

Die App hat ein helles und ein dunkles Farbschema (Tokens aus `[data-theme="dark"]` in `docs/ChargeShare-App-Design.html`, umgesetzt als `LightColors`/`DarkColors` in `ui/Theme.kt`).
Standard ist **System** (`isSystemInDarkTheme`); im Profil unter „Darstellung“ lässt sich **Hell / Dunkel / System** wählen. Die Wahl bleibt gespeichert
(`ThemeSettings` über `PreferenceStorage`: Android `SharedPreferences`, iOS `NSUserDefaults`, Desktop `java.util.prefs`, Web `localStorage`).
Kartenkacheln bleiben in beiden Modi hell (OpenStreetMap). Unter Android passen sich Statusleiste, Navigationsleiste und Fensterhintergrund an (`MainActivity`, `values-night/themes.xml`).

## Nächste Schritte

1. Server: Stripe gegen Testkonto pruefen (Google Pay/Apple Pay mit echten Testschlüsseln durchspielen), Zahlung nach OCPP-Abschluss auf tatsaechliche kWh abrechnen, RemoteStart/Stop
2. composeApp: Karte auf iOS/Web freischalten (nach Test), Gerätestandort mit Einwilligung
3. iOS auf einem Mac bauen und testen (Keychain-`TokenStore` ist ungetestet)
4. CI für iOS (macOS-Runner); Linux-CI läuft bereits (siehe „CI und Self-hosted-Runner“)
