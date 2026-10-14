# ChargeShare

🇬🇧 [English version](README.md)

Peer-to-Peer Wallbox-Sharing ("Airbnb für Wallboxen"), komplett in **Kotlin Multiplatform**.
Primärfarbe `#0B6E4F`, Akzent `#F0A500`.

> **Für Entwickler und KI-Agenten:** Projektwissen, Architektur und verbindliche Kotlin-Standards stehen in [`AGENTS.md`](AGENTS.md).

## Screenshots

### Android

| Einführung | Suche & Karte | Wallbox-Detail |
|---|---|---|
| <img src=".github/screenshots/android-onboarding.png" width="240"> | <img src=".github/screenshots/android-search-map.png" width="240"> | <img src=".github/screenshots/android-wallbox-detail.png" width="240"> |
| **Buchung** | **Host-Bereich** | **Profil** |
| <img src=".github/screenshots/android-booking.png" width="240"> | <img src=".github/screenshots/android-host-dashboard.png" width="240"> | <img src=".github/screenshots/android-profile.png" width="240"> |

### Web (Compose/Kotlin-Wasm)

| Suche (Listenansicht) | Anmeldung |
|---|---|
| <img src=".github/screenshots/web-search-list.png" width="400"> | <img src=".github/screenshots/web-login.png" width="400"> |

### Website (Infoseite)

<img src=".github/screenshots/website-landing.png" width="600">

### iOS

_Folgen._

Alle Screenshots zeigen Demo-Daten (`DEV_SEED`).

## Module

| Modul | Inhalt | Targets |
|---|---|---|
| `shared/` | Domain-Modelle, DTOs (JSON-Feldnamen wie im Go-Backend), Ktor-API-Client (`ChargeShareApi`, Bearer-Auth mit Auto-Refresh), Tests in `commonTest` | JVM, Android\*, iOS\*\*, Wasm |
| `composeApp/` | Gemeinsame Compose-Multiplatform-UI: Startbild (Splash), Einführung beim ersten Start, Login/Registrierung, Suche, Wallbox-Detail, Buchungsflow, Buchungen, Host-Dashboard, Profil; ViewModels, Repositories, `TokenStore` je Plattform | Android\*, iOS\*\*, Desktop-JVM, Wasm |
| `androidApp/` | Android-Application-Modul (`MainActivity` -> `App()`) | Android\* |
| `iosApp/` | SwiftUI-Hülle + XcodeGen-Spec (`project.yml`) | iOS\*\* |
| `server/` | Ktor-Backend: Netty, JWT, Exposed, Flyway, PostgreSQL, kotlinx.serialization | JVM |
| `web/` | Compose-Web (Kotlin/Wasm) Einstiegspunkt, nutzt `composeApp` | Wasm |

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

Compose verlangt in der Info.plist `CADisableMinimumFrameDurationOnPhone = true` (sonst Absturz beim Start auf dem iPhone).

Das Xcode-Projekt baut das Kotlin-Framework `ComposeApp` per Gradle-Run-Script
(`:composeApp:embedAndSignAppleFrameworkForXcode`).

Die Haupt-Tabs nutzen auf iOS eine **native UIKit-`UITabBar`** (`MainTabsChrome.ios.kt`, Deployment Target **18.0**);
der Tab-Inhalt bleibt Compose in derselben Komposition. Auf dem Simulator mit iOS 26 erscheint die Systemoptik (inkl. Liquid Glass) automatisch.
Das Submodule [`cmp-ios-chrome`](https://github.com/AMiketta/cmp-ios-chrome) (`third_party/cmp-ios-chrome`) ist optional und wird
von der App derzeit nicht gebraucht – ein GitHub-ZIP ohne Submodule baut also auch.

### App gegen das lokale Backend

```bash
./gradlew :server:runLocal                 # Backend + Demo-Daten (Terminal 1)
./gradlew :composeApp:run                  # Desktop-App, Adresse per CHARGESHARE_API_URL (Standard http://localhost:8080)
./gradlew :androidApp:installDebug         # Android; Emulator erreicht den Host unter http://10.0.2.2:8080
# andere Adresse (z. B. echtes Gerät im WLAN): ./gradlew -Pchargeshare.apiUrl=http://192.168.0.10:8080 :androidApp:installDebug
```

Demo-Logins: `demo-driver@chargeshare.local` / `demo-host@chargeshare.local`, Passwort `demo1234`.
Die Web-App liest die API-Adresse aus `?api=https://…` (Standard `http://localhost:8080`).
Die Suche zeigt auf Android, Desktop und iOS eine Karte (siehe [Karte](#karte)), im Web die Liste.

## Server starten

```bash
# optional: lokale Datenbank
docker compose up -d
export DATABASE_URL=postgres://chargeshare:chargeshare@localhost:5432/chargeshare

./gradlew :server:run
curl localhost:8080/health      # {"status":"ok","database":"ok"}
```

Ohne `DATABASE_URL` läuft der Server ohne Datenbank (`"database":"disabled"`). Beim Start mit Datenbank wendet
Flyway `server/src/main/resources/db/migration/V1__init.sql` und `V2__wallbox_description.sql` (Beschreibung der Wallbox) an;
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
| Passwort zurücksetzen (`POST /auth/forgot-password`, `POST /auth/reset-password`) | implementiert (Migration `V5__password_reset.sql`): `forgot-password` antwortet **immer** `204` (kein Hinweis, ob die Adresse existiert; Mailfehler bleiben unsichtbar) und ist wie Login ratenbegrenzt. Das Token (32 Zufallsbytes, base64url) steht nur als SHA-256 in der DB, gilt `PASSWORD_RESET_TTL_MINUTES` (Standard 60), ist einmalig, und eine neue Anfrage entwertet das alte. `reset-password` (`new_password` ≥ 8 Zeichen) → `204`, ungültiges/abgelaufenes/benutztes Token oder zu kurzes Passwort → `422`; danach sind alle Refresh-Tokens gelöscht (alle Geräte melden sich neu an). Die Mail enthält `{PUBLIC_BASE_URL}/reset-password?token=…`; die öffentliche Seite springt per `chargeshare://reset-password?token=…` in die App. **Offen: es gibt noch keinen echten Mailversand** – nur `LoggingMailer` (Dev: Link steht im Server-Log, in Produktion wird er nicht geloggt). Vor dem Livegang ein `Mailer` mit SMTP/Provider ergänzen |
| Wallbox-Fotos (`POST /wallboxes/{id}/photos`, `DELETE /wallboxes/{id}/photos/{name}`, öffentlich `GET /wallboxes/{id}/photos/{name}`) | implementiert: Upload als `multipart/form-data` (Feld `file`), nur der Besitzer der Wallbox, höchstens 8 Fotos pro Wallbox (sonst `409`). Der Server akzeptiert JPEG und PNG (geprüft an den Magic Bytes, höchstens 40 Megapixel), kodiert sie als JPEG neu (längste Kante 1600 px) und legt zusätzlich eine Vorschau mit 480 px an. **Dabei gehen EXIF- und GPS-Daten verloren.** Die EXIF-Drehung wendet der Server nicht an; das macht der Android-Picker vor dem Upload. Im Wallbox-Objekt steht der relative Pfad der großen Fassung, die kleine hat die Endung `-s.jpg`. `PUT` darf die Fotoliste nur umsortieren oder kürzen. Beim Löschen der Wallbox werden die Dateien entfernt |
| `POST /geocode` | implementiert (Login nötig): Adresse → Koordinaten über Nominatim (OpenStreetMap). Der Server hält die Nutzungsregeln ein: eigener User-Agent, höchstens eine Anfrage pro Sekunde, 24-Stunden-Cache, höchstens 20 Anfragen pro Nutzer und Minute (`429`). Nicht gefunden → `404`, Dienst nicht erreichbar oder abgeschaltet → `503`. Die App trägt bei „Koordinaten aus Adresse ermitteln“ die Treffer ein |
| `GET /bookings/{id}/receipt` | implementiert: Quittung als PDF (`application/pdf`, ohne Zusatzbibliothek erzeugt, `receipt/SimplePdf.kt`), nur für den Fahrer und nur bei Status `completed` (andere Nutzer `404`, nicht abgeschlossene Buchung `409`). Inhalt: Buchungsnummer, Wallbox, Zeitraum, kWh, Betrag, Plattformgebühr, Hinweis, dass es **keine Rechnung mit Umsatzsteuerausweis** ist |
| Gespeicherte Karten (`GET /payments/methods`, `DELETE /payments/methods/{id}`, `POST /payments/setup-intent`, `POST /payments/customer-session`) | implementiert für Stripe (Stripe-Kunde wird bei Bedarf angelegt, `users.stripe_customer_id`): `methods` liefert Marke, letzte vier Ziffern und Ablauf (`supported=false` ohne Stripe), `DELETE` fremder Karten `404`, `setup-intent` (SetupIntent, nur Karten) zum Speichern ohne Zahlung, `customer-session` lässt das PaymentSheet gespeicherte Karten anzeigen und neue merken. Mit dem Fake-Provider `supported=false` bzw. `503`. **Nur mit Testdoubles getestet, nicht gegen Stripe** |
| `POST /auth/apple` | implementiert, aber **nur mit `APPLE_BUNDLE_ID`** (prueft Signatur gegen Apples JWKS, `iss`, `aud`, `exp`); ohne Konfiguration `501`. Nicht gegen echte Apple-Tokens getestet (Test mit lokal signiertem Token) |
| Wallboxen (CRUD, Suche per `earth_distance`, Status, Wochenplan, Slots, Reviews-Liste) | implementiert |
| `GET /wallboxes/{id}/host` | implementiert, ohne Login: Host-Karte der Detailseite (`name` gekürzt auf „Vorname N.“, `member_since`, `sessions` = abgeschlossene Buchungen auf allen Wallboxen des Hosts, `rating_avg`/`rating_count` über alle seine Wallboxen, `is_superhost`). Superhost ist abgeleitet (`SuperhostCriteria`): mindestens 10 abgeschlossene Sessions **und** mindestens 5 Bewertungen **und** Durchschnitt ≥ 4,5. Keine Kontaktdaten |
| Favoriten (`GET /users/me/favorites`, `PUT\|DELETE /users/me/favorites/{wallbox_id}`) | implementiert (Migration `V3__favorites.sql`): `GET` liefert `{"wallbox_ids": [...]}`, neueste zuerst; `PUT`/`DELETE` antworten `204` und sind idempotent, `PUT` auf unbekannte Wallbox `404`. Favoriten verschwinden mit Nutzer oder Wallbox (`ON DELETE CASCADE`) |
| Buchungen (anlegen, Liste, Detail, stornieren, `confirm-payment`, `pay`, `problem`) | implementiert; Ueberlappungsschutz per Exclusion-Constraint + Advisory-Lock |
| Push-Geräte (`POST|DELETE /devices`) + Buchungs-Push-Hooks | MVP: Tokens + `LoggingPushNotifier` (siehe [Push-Benachrichtigungen](#push-benachrichtigungen-mvp)); echtes FCM/APNs folgt |
| `GET /payments/config` | implementiert: öffentliche Zahlungskonfiguration (siehe Abschnitt „Zahlung“); nur mit Login |
| Host (`/host/bookings`, `/host/earnings`, `/host/sessions`), `POST /reviews` | implementiert; Bewertung nur durch den Fahrer einer `completed`-Buchung, einmal pro Buchung (`409`), 1–5 Sterne, Kommentar ≤ 2000 Zeichen (leer = kein Kommentar); `Booking.reviewed` zeigt, ob schon bewertet wurde; Durchschnitt/Anzahl werden an der Wallbox exakt neu berechnet. `GET /host/earnings?tz=Europe/Berlin&from=&to=` liefert zusätzlich kWh-Summen, Vormonat, `month_change_pct` (`null`, wenn der Vormonat 0 € hatte) und `week` (Mo–So, 7 Tageswerte); Tage/Monate/Woche zählen nach **Buchungsbeginn** (`start_time`, nicht mehr nach Anlagezeit) in der Zeitzone `tz` (Default UTC, ungültig → `400`) |
| Zahlung | `PaymentProvider`-Interface: `FakePaymentProvider` (Dev) oder `StripePaymentProvider` (PaymentIntent mit Connect-Transfer + Plattformgebuehr, Refund, Connect-Onboarding, Webhook mit Signaturpruefung). Connect-Konten laufen über die Accounts-v2-API. Im Stripe-Testmodus geprüft: Onboarding-Link, Webhook (`payment_intent.succeeded`, `charge.refunded`) und Refund |
| OCPP 1.6J `GET /ocpp/{id}` (WebSocket) | implementiert: BootNotification, Heartbeat, Authorize, Start-/StopTransaction, MeterValues (nur Log), StatusNotification. RemoteStart/Stop (im Go-Code ungenutzt) nicht portiert |
| Rate-Limiting (Go: `ratelimit.go`) | portiert: `POST /auth/register\|login\|apple` max. 10 Versuche/Minute je Client-Adresse (`AUTH_RATE_LIMIT_*`), danach `429` mit `Retry-After`; im Speicher je Instanz; Refresh und übrige Routen ungebremst |

Bewusste Abweichungen vom Go-Code (Fehler/Luecken dort): Stornieren liefert `204` (Client laedt die Buchung danach neu); Booking-Insert vor
Payment-Intent; Exclusion-Verletzung -> `409` statt `500`; `confirm-payment` prueft den Zahlungsstatus beim Provider (`402`);
Konto loeschen entfernt abhaengige Buchungen/Reviews; E-Mail wird kleingeschrieben; Rolle wird aus der DB statt aus dem Token gelesen;
Slots in der Vergangenheit werden nicht angeboten; belegte Slots der Zukunft stehen zusätzlich in `locked` (Antwort von `GET /wallboxes/{id}/slots`, für die ausgegraute Darstellung im Buchungsraster); unbekannte OCPP-Ladepunkte (`ocpp_id` an keiner Wallbox) werden abgelehnt.

## Karte

Die Suche hat einen Umschalter **Karte / Liste**; auf Android, Desktop und iOS ist die Karte die Startansicht.
Jede Wallbox ist ein Pin (grün = verfügbar, grau = offline); ein Tipp auf den Pin öffnet die Detailseite.
Die Kamera startet auf dem Gerätestandort (Android/iOS, nach Freigabe) bzw. auf Bremen und springt auf den Umfang der Suchergebnisse. Über „Filter“ lassen sich die Suchfilter über der Karte einblenden.

**Standort:** Beim Öffnen der Suche erscheint einmalig ein Hinweis; danach der Systemdialog (Android: Fine/Coarse Location, iOS: When-In-Use, Text in `NSLocationWhenInUseUsageDescription`). Bei Ablehnung oder auf Desktop/Web bleibt Bremen, mit Hinweis und Chip „In der Nähe“. Der Standort wird nicht gespeichert.

- **Android / Desktop:** [MapCompose-MP](https://github.com/p-lr/MapComposeMP) (`ovh.plrapps:mapcompose-mp`, Apache-2.0) mit OpenStreetMap-Kacheln über `OsmTileSource` (Quellenangabe „© OpenStreetMap-Mitwirkende“ auf der Karte). **Kein Google Maps**, kein API-Key.
- **iOS:** native **Apple MapKit** (`MKMapView` über Compose `UIKitView`). Apple blendet die eigene Karten-Quellenangabe ein. Kein MapCompose auf iOS (Source-Set `mapcomposeMain` nur für Android/Desktop).
- **Plattformen:** `expect val isMapSupported` (`platform/MapSupport.kt`): Android, Desktop und iOS = `true`; **Web = `false`** – dort zeigt die Suche die Liste mit Hinweis, kein Umschalter. Die iOS-MapKit-Karte bitte einmal im Simulator prüfen.
- **Ohne Internet** (MapCompose/OSM): Kartenfläche leer, Pins und Navigation funktionieren weiter. MapKit braucht Netz für Kacheln, zeigt aber ggf. Cache.
- **Produktion (OSM):** die OSM-Server sind nur für moderate Nutzung gedacht ([Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/)). Vor einem Store-Release eigenen Kachelserver oder Anbieter mit Vertrag (`OsmTileSource(baseUrl = …)`). MapKit braucht das nicht.
- **Code:** `presentation/map/` (Web-Mercator, `Wallbox.toPin()` mit lat/lng, Kartenausschnitt – getestet), `data/OsmTileSource.kt` (nur MapCompose), `ui/map/WallboxMap` als `expect`/`actual` (`mapcomposeMain` / `iosMain` / `wasmJsMain`). `WallboxMapHolder` hält den Kartenzustand prozessweit (kein hartes Abschalten beim Tab-Wechsel, PR #38).

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

**Buchbarkeit nach Auszahlungskonto (nur mit echtem Stripe):** Wallboxes sind erst buchbar, wenn der Host das Connect-Onboarding abgeschlossen hat
(`users.payouts_ready`, Migration V4). Davor antwortet `POST /bookings` mit `409` und Code `HOST_PAYOUTS_PENDING` und deutscher Meldung; die Wallbox
meldet `payouts_ready: false` (Liste und Detail), die App zeigt „Bald buchbar“ und sperrt den Buchen-Button. Der Server fragt den Kontostatus bei Stripe
ab (`stripe_transfers` aktiv): beim Buchungsversuch, nach `GET /payments/onboard/return` und alle 2 Minuten im Hintergrund (`PayoutSweeper`). Ein
Stripe-Ausfall führt zu `409`, nie zu `500`. Der Dev-Seed-Host ist freigeschaltet; mit dem Fake-Provider gibt es die Sperre nicht.

**Onboarding in der App (Host-Dashboard):** Die Karte „Auszahlungen“ zeigt den Status *nicht eingerichtet / in Prüfung / bereit*
(`GET /payments/payout-status`, aus der Datenbank; `not_required` mit dem Fake-Provider blendet die Karte aus). „Auszahlungen einrichten“ ruft
`POST /payments/onboard` und öffnet den Stripe-Link im Browser. Stripe kennt keine eigenen URL-Schemata und leitet deshalb auf zwei öffentliche
Seiten des Servers (`GET /onboarding/return`, `GET /onboarding/refresh`), die per Deep Link `chargeshare://onboarding-done` bzw.
`chargeshare://onboarding-refresh` zurück in die App springen (Android: Intent-Filter, `launchMode=singleTask`). Die App prüft dann sofort
`POST /payments/payout-status/refresh` (fragt Stripe, speichert, antwortet mit dem neuen Status) – kein Warten auf den 2-Minuten-Job. Bei einem
abgelaufenen Link (`onboarding-refresh`) holt die App automatisch einen neuen. Adressen: `ONBOARDING_RETURN_URL`/`ONBOARDING_REFRESH_URL` (ausdrücklich),
sonst `PUBLIC_BASE_URL` + Pfad, sonst die Adresse der Anfrage (Emulator: `http://10.0.2.2:8080`). iOS: Deep Link noch nicht angebunden (URL-Schema + Brücke
fehlen), Web: Link öffnet in neuem Tab, Rückkehr nur manuell über „Status prüfen“.

**Gespeicherte Karten und Quittungen in der App:** Profil → „Zahlungsmethoden“ listet die bei Stripe gespeicherten Karten (Marke, letzte vier Ziffern,
Ablauf), erlaubt Entfernen und – auf Android – „Karte hinzufügen“ (SetupIntent im PaymentSheet). Beim Bezahlen zeigt das PaymentSheet die gespeicherten
Karten (Customer Session) und bietet an, neue zu merken. In der Buchungsliste gibt es bei abgeschlossenen Buchungen „Quittung (PDF)“ (Android:
Teilen-Dialog, Desktop: PDF-Programm, Web: Download). Hinweis: Das Speichern/Entfernen von Karten ist nur gegen Testdoubles geprüft, nicht gegen Stripe; iOS hat
keinen Kartenspeicher-Dialog.

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
| `PUBLIC_BASE_URL`, `ONBOARDING_RETURN_URL`, `ONBOARDING_REFRESH_URL` | Öffentliche Server-Adresse (`https://api.chargeshare.app`) für die Rückkehr-Seiten des Stripe-Onboardings; Standard: Adresse der Anfrage. Die URLs können einzeln überschrieben werden. In Produktion `PUBLIC_BASE_URL` setzen |
| `PHOTO_STORAGE_DIR`, `PHOTO_MAX_UPLOAD_BYTES` | Ordner für Wallbox-Fotos (Standard `data/photos`) und Größenlimit pro Upload (Standard 10 000 000 Byte). **Der Ordner muss auf einem dauerhaften Volume liegen und gesichert werden**, sonst sind die Fotos nach einem Neustart weg |
| `GEOCODER_URL`, `GEOCODER_USER_AGENT`, `GEOCODER_COUNTRY_CODES` | Adressdienst (Standard `https://nominatim.openstreetmap.org`; gesetzt, aber leer = abgeschaltet), Nominatim verlangt einen identifizierenden User-Agent mit Kontakt (in Produktion setzen) und Ländereinschränkung (Standard `de`). Der öffentliche Dienst ist nur für geringe Nutzung gedacht |
| `PASSWORD_RESET_TTL_MINUTES` | Gültigkeit des Passwort-Reset-Links in Minuten (Standard 60) |
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

## CI

CI läuft in GitHub Actions (`.github/workflows/ci.yml`): gitleaks-Secret-Scan, Tests, Server, Desktop-Tests, Web und Android-Debug-APK.
Standard ist ein GitHub-gehosteter Runner (`ubuntu-latest`). Über die Repository-Variable `CI_RUNS_ON` lässt sich ein eigener Runner wählen
(z. B. `gh variable set CI_RUNS_ON --body '["self-hosted","chargeshare"]'`) – **bei einem öffentlichen Repo niemals für Pull Requests aus Forks**,
denn Self-hosted-Runner führen fremden PR-Code auf dem eigenen Rechner aus.

## Dunkelmodus

Die App hat ein helles und ein dunkles Farbschema (umgesetzt als `LightColors`/`DarkColors` in `ui/Theme.kt`).
Standard ist **System** (`isSystemInDarkTheme`); im Profil unter „Darstellung“ lässt sich **Hell / Dunkel / System** wählen. Die Wahl bleibt gespeichert
(`ThemeSettings` über `PreferenceStorage`: Android `SharedPreferences`, iOS `NSUserDefaults`, Desktop `java.util.prefs`, Web `localStorage`).
Kartenkacheln bleiben in beiden Modi hell (OpenStreetMap). Unter Android passen sich Statusleiste, Navigationsleiste und Fensterhintergrund an (`MainActivity`, `values-night/themes.xml`).

## Nächste Schritte

1. Server: Stripe gegen Testkonto pruefen (Google Pay/Apple Pay mit echten Testschlüsseln durchspielen), Zahlung nach OCPP-Abschluss auf tatsaechliche kWh abrechnen, RemoteStart/Stop
2. composeApp: Karte auf Web freischalten (nach Browser-Test); iOS-Karte und Standortfreigabe im Simulator prüfen
3. iOS auf einem Mac bauen und testen (Keychain-`TokenStore` ist ungetestet)
4. CI für iOS (macOS-Runner); Linux-CI läuft bereits (siehe [CI](#ci))

## Lizenz

Apache License 2.0, siehe [LICENSE](LICENSE) und [NOTICE](NOTICE). Name und Logo „ChargeShare“ sind von der Lizenz ausgenommen.

## Push-Benachrichtigungen (MVP)

Ziel: Host und Fahrer bei wichtigen Buchungs-/Lade-Ereignissen benachrichtigen.
Dieses MVP **versendet noch keine echten FCM/APNs-Nachrichten** – der Server loggt sie
(`LoggingPushNotifier`). Die App kann Gerätetokens registrieren; ohne Firebase/APNs-Setup
bleibt das Token leer.

### Ereignis → Empfänger

| Ereignis | Auslöser | Host | Fahrer |
|---|---|---|---|
| Wallbox freischalten | Buchung → `confirmed` | ✅ Erinnerung | — |
| Buchung bestätigt / Storno-Option | Buchung → `confirmed` | — | ✅ + Deep Link `chargeshare://booking/{id}?action=cancel` |
| Laden gestartet | OCPP `StartTransaction` → `active` | ✅ | ✅ |
| Laden beendet | OCPP `StopTransaction` → `completed` | ✅ | ✅ |
| kWh eintragen | nach Laden beendet | ✅ | — |
| Buchung storniert | `DELETE /bookings/{id}` (nur aus `confirmed`/`active`) | ✅ | — |
| Problem gemeldet | `POST /bookings/{id}/problem` | ✅ | — |

Titel und Texte (DE) liegen in `shared/.../model/Push.kt` (`PushNotificationKind`).

### API

| Methode | Pfad | Auth | Zweck |
|---|---|---|---|
| `POST` | `/api/v1/devices` | JWT | Token registrieren (`platform`: `android`\|`ios`, `token`) → `204` |
| `DELETE` | `/api/v1/devices` | JWT | Token abmelden (`token`) → `204` |
| `POST` | `/api/v1/bookings/{id}/problem` | JWT (Fahrer) | Problemtext an Host → Push + `204` |

### Server-Verhalten

- Tabelle `push_devices` (Flyway `V6__push_devices.sql`), Upsert per Token.
- `PushNotifier` + Standard `LoggingPushNotifier` (CI/Dev ohne Secrets).
- Hooks in `BookingService` (confirm/cancel) und `OcppHandler` (start/stop).

### App

- Nach Login/`restoreSession` → `PushDeviceRepository.syncRegistration()` (No-Op ohne Token).
- Beim Logout → gespeichertes Token abmelden.
- Android: Token-Quelle ist vorbereitet; FCM-Dienst ist **Stub** (`androidApp/.../push/FcmMessagingService.kt`).
- iOS: Permission/Token-Platzhalter; APNs folgt später.
- Keine gefakten OS-Notifications: ohne Provider sieht man die Ereignisse nur im Server-Log.

### Firebase später aktivieren

1. Firebase-Projekt anlegen, Android-App `app.chargeshare`, `google-services.json` nach `androidApp/`.
2. Version Catalog: `firebase-messaging` + Plugin `com.google.gms.google-services`.
3. `FcmMessagingService` von `FirebaseMessagingService` ableiten; Manifest-Eintrag.
4. `AndroidPushTokenSource.currentToken()` per `FirebaseMessaging.getInstance().token` füllen.
5. Server: `FcmPushNotifier` gegen FCM HTTP v1 (Service-Account) implementieren und statt Logging verdrahten.

### Geplante Server-Env (später)

```bash
# Noch nicht ausgewertet – Platzhalter für den echten Notifier
# FIREBASE_PROJECT_ID=
# GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
# PUSH_PROVIDER=logging|fcm   # Standard: logging
```

iOS (später): APNs-Key in Firebase oder direkt; `aps-environment` in Entitlements; Token an `POST /devices` mit `platform=ios`.
