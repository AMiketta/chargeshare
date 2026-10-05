# AGENTS.md – Projektwissen für KI-Agenten und Menschen

Diese Datei ist die **verbindliche Arbeitsgrundlage** für jeden, der an ChargeShare Code ändert – KI-Agent oder Mensch.
Sie beschreibt, was das Projekt ist, wie es gebaut wird und **nach welchen Standards Code geschrieben wird**.
Wer eine Regel hier ändern will, ändert zuerst diese Datei (eigener Commit) und begründet es.

> Leitsatz: **Moderner, idiomatischer Kotlin-Code, den ein Mensch ohne Rückfragen versteht.**
> Lesbarkeit schlägt Cleverness. Wenn eine Zeile eine Erklärung braucht, ist sie meistens zu clever.

---

## 1. Projektziel

**ChargeShare** ist eine Peer-to-Peer-Plattform für Wallbox-Sharing in Deutschland („Airbnb für Wallboxen“):
private Wallbox-Besitzer geben ihre Ladestation zeitgesteuert frei, Fahrer buchen einen Zeitslot und zahlen automatisch.

- **Fachliche Quelle der Wahrheit:** `docs/ChargeShare_App_Konzept.pdf` (Konzept, April 2026) und
  `docs/ChargeShare-App-Design.html` (Farben, Layout, Screens).
- **Zielgruppen**
  - **Driver (Fahrer):** E-Auto-Fahrer ohne eigene Lademöglichkeit. Sucht Wallboxen in der Nähe, bucht Slots, storniert, bewertet.
  - **Host (Anbieter):** Wallbox-Besitzer. Legt Wallboxen an, setzt Preis und Verfügbarkeit, sieht Einnahmen und Sessions.
  - Rollen im Backend: `driver`, `host`, `both`, `admin` (`admin` ist nicht selbst setzbar).
- **MVP-Funktionen:** Suche (Umkreis/Filter), Wallbox-Detail, Zeitslot-Buchung mit Zahlung, Buchungsliste mit Storno,
  Host-Dashboard, Profil. OCPP-1.6J-Anbindung der Wallboxen im Backend.
- Das Projekt ist ein **Kotlin-Multiplatform-Neubau** der früheren Go-Backend- und SwiftUI-Implementierung (`legacy/`).

## 2. Module und Abhängigkeitsregeln

| Modul | Inhalt | Targets |
|---|---|---|
| `shared/` | Domain-Modelle/DTOs (`model/`), Ktor-Client `ChargeShareApi` (`api/`), `TokenStore`, JSON-Konfiguration | JVM, Wasm, Android\*, iOS\*\* |
| `composeApp/` | Gemeinsame Compose-Multiplatform-UI, ViewModels, Repositories, DI-Container | Desktop-JVM, Wasm, Android\*, iOS\*\* |
| `androidApp/` | Android-Application (`MainActivity` → `App(...)`), Manifest | Android\* |
| `iosApp/` | dünne SwiftUI-Hülle + XcodeGen-Spec (`project.yml`), ruft `MainViewController()` | iOS\*\* |
| `web/` | Wasm-Einstiegspunkt (`ComposeViewport`), nutzt `composeApp` | Wasm |
| `server/` | Ktor-Backend (Netty, JWT, Exposed, Flyway, PostgreSQL, OCPP) | JVM |
| `docs/` | Konzept (PDF) und Design (HTML) | – |
| `legacy/` | Go-Backend und SwiftUI-App – **nur Referenz, wird nicht gebaut, nicht erweitern** | – |

\* nur wenn ein Android SDK gefunden wird. \*\* nur auf macOS (oder erzwungen, siehe Abschnitt 4).

**Abhängigkeitsrichtung (nie umkehren, keine Zyklen):**

```
androidApp ─┐
iosApp ─────┼─▶ composeApp ─▶ shared
web ────────┘                    ▲
server ──────────────────────────┘   (nutzt nur die Modelle/JSON-Konfiguration aus shared)
```

- `shared` kennt weder Compose noch Server-Code. Es enthält nur, was **alle** Clients und der Server teilen müssen.
- `server` darf `shared` nutzen, **nie** `composeApp`. `composeApp` darf **nie** auf `server` zugreifen.
- Plattformspezifisches (Keychain, Keystore, localStorage, Context) gehört in `expect/actual`-Schnittstellen **mit kleiner Oberfläche**
  im jeweiligen Source-Set (`androidMain`, `iosMain`, `desktopMain`, `wasmJsMain`) – nie `if (platform == …)` in Common-Code.
- Neue Geschäftslogik zuerst in `commonMain` anlegen, nicht in einem plattformspezifischen Modul.

### Aufbau von `composeApp/src/commonMain/kotlin/app/chargeshare/`

| Paket | Inhalt |
|---|---|
| `ui/` | `App()`, Theme, Navigation (Wurzel), wiederverwendbare Komponenten (`ui/components`), Strings (`ui/Strings.kt`), ein Unterpaket pro Feature (`ui/auth`, `ui/wallbox`, `ui/map`, `ui/booking`, `ui/host`, `ui/profile`) mit Screens (`@Composable`) |
| `presentation/` | ViewModels (`androidx.lifecycle.ViewModel`) und `UiState`-Klassen, ein Unterpaket pro Feature (`auth`, `session`, `wallbox`, `map`, `booking`, `host`, `profile`). **Keine Compose-UI-Imports** |
| `data/` | Repositories (Schnittstelle + `Api…`-Implementierung über `ChargeShareApi`), `Outcome`/`AppError`/`runCatchingOutcome`, `PersistedTokenStore`, Slot-Zeitlogik |
| `payment/` | `PaymentLauncher` (Schnittstelle zum Zahlungsfenster), `PaymentRequest`/`PaymentResult`/`PaymentMethodChoice`, `UnsupportedPaymentLauncher` (Desktop/Web). Plattform-Implementierungen: Android `androidApp/.../StripePaymentLauncher`, iOS `iosMain/.../IosPaymentBridge.kt` + Swift `iosApp/.../StripePaymentBridge.swift` |
| `platform/` | `ReceiptSharer` (Quittungs-PDF teilen/öffnen/herunterladen), `UrlOpener` (Browser öffnen; Implementierungen je Plattform, werden dem `AppContainer` übergeben); `expect fun createSecureTokenStore(PlatformContext)` mit `actual` je Plattform (Android-Keystore, iOS-Keychain, Desktop-Datei, Web-localStorage); `expect fun createPreferenceStorage(PlatformContext)` für nicht geheime Einstellungen (Dunkelmodus: SharedPreferences, NSUserDefaults, java.util.prefs, localStorage); `PlatformContext` ist eine normale Klasse (Android: `Context` im `handle`). `expect val isMapSupported`: ob die Plattform die Karte zeigt (Android/Desktop/iOS `true`, Web `false` → nur Liste) |
| `di/` | `AppContainer` – manuelle Dependency Injection |

## 3. Tech-Stack (tatsächliche Versionen, Quelle: `gradle/libs.versions.toml`)

| Bereich | Version |
|---|---|
| Kotlin | 2.4.20 (Gradle 9.8.0 per Wrapper, JDK 17+) |
| Android Gradle Plugin | 9.4.1 (`compileSdk 37`, `minSdk 24`, `targetSdk 36`) |
| Compose Multiplatform | 1.12.1; Material3 `1.12.0-alpha03` (für CMP 1.12 gibt es kein stabiles M3) |
| kotlinx.coroutines / serialization | 1.11.0 / 1.11.0 |
| Ktor (Client und Server) | 3.6.0 |
| Exposed / Flyway / HikariCP | 1.5.0 / 13.9.0 / 7.1.0 |
| PostgreSQL-Treiber / Logback | 42.7.13 / 1.6.5 |
| bcrypt / Stripe Java / embedded-postgres | 0.10.2 / 34.0.0 / 2.2.2 |
| androidx.activity-compose | 1.12.4 |
| Stripe Android SDK (`androidApp`) / Stripe iOS SDK (SPM, `iosApp/project.yml`) | 23.21.0 / 26.12.1 |

Zusätzlich in `composeApp`: kotlinx-datetime 0.8.0, MapCompose-MP 1.1.3 (Karte mit OpenStreetMap-Kacheln, kein API-Key; reines Compose Multiplatform), `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` 2.10.0,
`org.jetbrains.compose.ui:ui-backhandler` 1.12.1 (Zurück-Taste) und – nur Desktop – `kotlinx-coroutines-swing` (liefert `Dispatchers.Main` für `viewModelScope`).
**Alle Versionen nur im Version Catalog pflegen**, nie in einzelnen `build.gradle.kts`.
Dependency-Injection-Framework: **keines** (manuell per Konstruktor, siehe Abschnitt 6). Koin o. Ä. nur nach Absprache und Eintrag hier.

## 4. Build-, Test- und Lauf-Befehle

Alle Befehle im Repo-Root. Auf der Box (Linux) zusätzlich die Umgebungsvariablen setzen:

```bash
export JAVA_HOME=/workspace/tools/jdk-17.0.20.1+1
export ANDROID_HOME=/workspace/tools/android-sdk
```

```bash
# Kern: Tests des shared-Moduls + kompletter Server-Build inkl. Tests
./gradlew :shared:jvmTest :server:build

# UI-Logik (ViewModel-Tests) und Desktop-Artefakt
./gradlew :composeApp:desktopTest :composeApp:desktopJar

# Web (Wasm) und Android
./gradlew :web:wasmJsBrowserDistribution
./gradlew :androidApp:assembleDebug

# Desktop-Vorschau der UI (Backend muss laufen; Adresse per CHARGESHARE_API_URL, Standard http://localhost:8080)
./gradlew :composeApp:run
# ohne GPU/OpenGL (z. B. Xvfb auf der Box): SKIKO_RENDER_API=SOFTWARE DISPLAY=:99 ./gradlew :composeApp:run

# Vollständige Verifikation (muss vor jedem Abschluss grün sein)
./gradlew :shared:jvmTest :server:build :composeApp:desktopTest :composeApp:desktopJar \
          :web:wasmJsBrowserDistribution :androidApp:assembleDebug
```

**API-Adresse der Clients:** Android per BuildConfig (Standard `http://10.0.2.2:8080`, ändern mit `-Pchargeshare.apiUrl=http://192.168.0.10:8080`),
Desktop per Umgebungsvariable `CHARGESHARE_API_URL`, Web per Query-Parameter (`index.html?api=https://…`, nur `http(s)://`), iOS-Parameter von `MainViewController(apiBaseUrl)`
(Standard `http://localhost:8080`).

**Plattform-Schalter** (Gradle-Properties, ausgewertet in `settings.gradle.kts`):

| Property | Wirkung |
|---|---|
| `-Pchargeshare.android=true\|false` | Android-Targets erzwingen/abschalten (Standard: an, wenn ein SDK via `ANDROID_HOME`/`ANDROID_SDK_ROOT`/`local.properties` gefunden wird) |
| `-Pchargeshare.ios=true\|false` | iOS-Targets erzwingen/abschalten (Standard: nur auf macOS). Auf Linux ist dann **nur Kompilierung zu Klibs** möglich, **kein** Framework-Link: `./gradlew -Pchargeshare.ios=true :composeApp:compileKotlinIosArm64` |

**Backend lokal starten (ohne Docker):**

```bash
./gradlew :server:runLocal      # eingebettetes PostgreSQL (Daten in server/.local-pg) + Server auf 0.0.0.0:8080
curl localhost:8080/health      # {"status":"ok","database":"ok"}
```

Mit Docker alternativ `docker compose up -d` und `DATABASE_URL=postgres://chargeshare:chargeshare@localhost:5432/chargeshare ./gradlew :server:run`.
Konfiguration über Umgebungsvariablen (`.env.example` zeigt die Namen).

Im Entwicklungsmodus (`ENV=development`, Standard) gilt:

- **Dev-Seed:** 8 Beispiel-Wallboxen in Bremen (Name beginnt mit `[Beispiel]`, Verfügbarkeit täglich 06:00–22:00 UTC) und zwei Demo-Logins:
  - `demo-driver@chargeshare.local` / `demo1234`
  - `demo-host@chargeshare.local` / `demo1234`
  - abschaltbar mit `DEV_SEED=false`; in Produktion verboten.
- **Stripe Connect:** Konten entstehen über die **Accounts-v2-API** (`/v2/core/accounts`, Express-Dashboard, Empfänger-Konfiguration mit `stripe_transfers`); v1 `POST /v1/accounts` ist für neue Plattformen gesperrt. Ein Host kann erst Zahlungen empfangen, wenn er das Onboarding im Stripe-Link abgeschlossen hat – bis dahin lehnt Stripe Destination Charges mit `insufficient_capabilities_for_transfer` ab.
- **Fake-Zahlung:** ohne `STRIPE_SECRET_KEY` sind Buchungen sofort `confirmed`/bezahlt; `GET /payments/config` meldet dann `provider: fake` und die App öffnet kein Zahlungsfenster.
- **API-Adresse aus Client-Sicht:** Android-Emulator `http://10.0.2.2:8080` (= Host-Rechner), Desktop/Web/iOS-Simulator `http://localhost:8080`.
  Die Adresse steckt im `AppContainer`, den die Einstiegspunkte erzeugen; sie ist nicht in der UI hartkodiert.

## 5. API-Vertrag

- **Quelle der Wahrheit:** die Modelle in `shared/src/commonMain/kotlin/app/chargeshare/shared/model/` (DTOs, JSON-Feldnamen per `@SerialName`),
  der Client `shared/.../api/ChargeShareApi.kt` und im `README.md` der Abschnitt **„API-Stand“** (was implementiert, was bewusst anders als in Go).
  Client und Server nutzen **dieselben** Modelle. Ändert sich der Vertrag, ändern sich Modell, Client, Server und Tests **im selben Commit**.
- **Basis:** `/api/v1`; Health: `GET /health`; OCPP-WebSocket: `GET /ocpp/{chargepoint_id}`.
- **Endpunkte** (`ChargeShareApi`):
  - Auth: `POST /auth/register|login|apple|refresh|logout`; Nutzer: `GET|PUT|DELETE /users/me`, `GET /users/me/stats` (Profil-Kennzahlen `sessions`, `kwh_charged`, `rating_avg` (nullable), `rating_count`), `PUT /users/me/role`, Favoriten `GET /users/me/favorites` (`wallbox_ids`, neueste zuerst), `PUT|DELETE /users/me/favorites/{wallbox_id}` (idempotent, `204`)
  - Wallboxen: `GET /wallboxes` (Query `lat,lng,radius,connector,min_power,max_price,limit,offset`; `limit` 1..100, Standard 20; Standard-Radius 10 km, nur mit `lat`+`lng`),
    `GET|PUT|DELETE /wallboxes/{id}`, `POST /wallboxes`, `PUT /wallboxes/{id}/status`, `GET|PUT /wallboxes/{id}/availability`,
    `GET /wallboxes/{id}/slots?date=YYYY-MM-DD` (`slots`: freie 30-Minuten-Slots, `locked`: im Fenster liegende, aber belegte Slots für die graue Darstellung; `HH:MM`, **UTC**; Verfügbarkeitsfenster dürfen um `24:00` enden), `GET /wallboxes/{id}/reviews`, `GET /wallboxes/{id}/host` (öffentliche Host-Karte: gekürzter Name, `member_since`, `sessions`, Bewertungen, abgeleitetes `is_superhost`, Schwellen in `SuperhostCriteria`).
    Ändern/Deaktivieren (`PUT /wallboxes/{id}`, `/status`, `/availability`) darf nur der Eigentümer (oder `admin`), sonst `403`.
    `description` (optional, ≤ 1000 Zeichen, getrimmt; bei `PUT` löscht `""` den Text). Grenzen in `WallboxLimits` (Name ≤ 200, Leistung ≤ 350 kW,
    Preis ≤ 5 €/kWh – bewusste Plausibilitätsgrenzen gegen Tippfehler). Preisänderungen gelten nur für neue Buchungen; eine Änderung der Verfügbarkeit
    oder „Deaktivieren“ storniert **keine** bestehenden Buchungen.
  - Buchungen: `GET|POST /bookings` (`Booking.reviewed`: schon bewertet), `GET|DELETE /bookings/{id}` (Storno antwortet `204`), `POST /bookings/{id}/pay` („Jetzt bezahlen“: `client_secret` einer `pending`-Buchung, nur Fahrer), `POST /bookings/{id}/confirm-payment`, `POST /reviews`
  - Host: `GET /host/bookings|earnings|sessions` (nur Rollen `host`/`both`/`admin`, sonst `403`; `earnings` nimmt `tz` (IANA, Default UTC) und rechnet Monat/Vormonat/Woche/kWh nach Buchungsbeginn, Logik rein in `EarningsCalculator`), `POST /payments/onboard` (Link; Stripe leitet auf die öffentlichen Seiten `GET /onboarding/return|refresh` des Servers, die per Deep Link `chargeshare://onboarding-done|onboarding-refresh` in die App springen; Adressen: `PUBLIC_BASE_URL`/`ONBOARDING_*_URL`, sonst Anfrage-Adresse), `GET /payments/payout-status` (`PayoutState` `not_required|not_started|pending|ready`, aus der DB), `POST /payments/payout-status/refresh` (fragt Stripe sofort, speichert, liefert den Status), `GET /payments/config` (öffentliche Zahlungskonfiguration ohne Secrets, mit Login)
- **JSON:** Feldnamen `snake_case` (`power_kw`, `price_per_kwh`, `start_time`, …), Zeiten als RFC-3339-Strings (UTC), Aufzählungen kleingeschrieben
  (`type2`, `ccs`, `chademo`, `type1`; `pending`, `confirmed`, `active`, `completed`, `cancelled`, `failed`). Gemeinsame Konfiguration: `ChargeShareJson`
  (`ignoreUnknownKeys`, `explicitNulls=false`, `coerceInputValues`).
- **Fehlerformat:** HTTP-Status ≠ 2xx mit Body `{"error": "…", "code": "VALIDATION_ERROR"}`. Der Client wirft daraus eine `ApiException(status, code, message)`.
  Codes stehen in `ErrorCodes` (`RATE_LIMITED`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `VALIDATION_ERROR`, `CONFLICT`, `INTERNAL_ERROR`, `BAD_REQUEST`, `PAYMENT_REQUIRED`, `NOT_IMPLEMENTED`, `HOST_PAYOUTS_PENDING`).
  Die Fehlertexte des Servers sind **englisch und technisch** – sie werden nie 1:1 dem Nutzer gezeigt, sondern in `AppError` übersetzt (Abschnitt 8).
  Ausnahme: Stripe-Fehler beim Connect-Onboarding (`PaymentService.onboardingUrl`) kommen als `503`/`502` mit lesbarer deutscher Meldung; Stripe-Rohtexte und Schlüssel werden nie durchgereicht oder geloggt.
  Zweite Ausnahme: `409 HOST_PAYOUTS_PENDING` (Wallbox eines Hosts ohne abgeschlossenes Stripe-Onboarding, `PayoutService`) hat eine deutsche Meldung; die App übersetzt sie in `AppError.HostPayoutsPending`. Nur mit `PaymentProvider.requiresPayoutAccount` (Stripe), nicht mit dem Fake-Provider.
- **Auth:** Access-JWT (15 min) per `Authorization: Bearer`; Refresh-Token rotiert bei jedem Refresh (Einmalverwendung).
  Der Client (`createHttpClient`) erneuert bei `401` automatisch; scheitert das, wird der `TokenStore` geleert.
- **Geld:** Beträge sind `Double` in Euro (Vertragsentscheidung aus dem Go-Backend). Nie selbst mit Fließkomma „weiterrechnen“,
  sondern Server-Werte (`total_amount`) anzeigen; Schätzungen in der UI nur als „ca.“ kennzeichnen.

## 6. Architektur

**Clean Architecture in drei Schichten pro Client-Feature**, Abhängigkeit immer nach innen:

```
ui (Composables)  ─▶  presentation (ViewModel + UiState)  ─▶  data (Repository)  ─▶  shared (ChargeShareApi)
```

- **UDF/MVI:** ein `ViewModel` pro Screen/Flow hält einen **unveränderlichen** `UiState` (`data class`/`sealed interface`) in einem
  `StateFlow` (`MutableStateFlow` privat, `StateFlow` öffentlich). Die UI sendet **Events** als Funktionsaufrufe (`onXyz()`), nie direkt Zustand.
  Einmalige Ereignisse (Navigation, Snackbar) sind **Teil des Zustands** (z. B. `message: UiMessage?` mit `onMessageShown()`), kein `SharedFlow`.
- **Repository-Muster:** UI/ViewModels sprechen nur mit Repository-**Schnittstellen** (`interface WallboxRepository`), nie mit Ktor oder `ChargeShareApi`.
  Die Implementierung übersetzt Exceptions in `Outcome.Failure(AppError)`.
- **Dependency Injection per Konstruktor.** Abhängigkeiten (Repository, `CoroutineDispatcher`, Uhr) werden übergeben, nie im ViewModel erzeugt.
  Verdrahtet wird zentral im `AppContainer` (`di/`), der `App()` einmal pro Prozess erzeugt. Globale Singletons/`object` mit Zustand sind verboten.
- **Navigation:** zustandsbasiert (sealed `Screen` + Back-Stack im `NavigationState`), Tabs über `MainTab`. Kein Compose-Code in ViewModels.
- **Zahlung:** Das Zahlungsfenster gehört der Plattform (Activity bzw. ViewController). Das `BookingFlowViewModel` kennt nur `PaymentRequest`/`PaymentResult`; die UI (`BookingFlowRoute`) öffnet das Fenster über den `PaymentLauncher`, den `App(container, paymentLauncher)` bekommt. Neue Plattform-Zahlungsarten ⇒ `PaymentMethodChoice` erweitern, Launcher und `Strings.paymentMethod` ergänzen.
- Domain-Typen aus `shared` (`Wallbox`, `Booking`, …) dürfen bis in die UI durchgereicht werden. UI-spezifische Aufbereitung (formatierte Texte)
  passiert in der UI oder in kleinen, getesteten Formatter-Funktionen, nicht in den DTOs.

## 7. Kotlin-Standards (verbindlich)

**Grundlage:** die [offiziellen Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) (`kotlin.code.style=official` ist gesetzt).

Idiomatisches Kotlin:

- `val` vor `var`; veränderlichen Zustand klein halten und kapseln. Collections als `List`/`Map`/`Set` (read-only) exponieren.
- **`data class`** für Werte, **`sealed interface`/`sealed class`** für geschlossene Fallunterscheidungen, **`@JvmInline value class`** für typisierte Primitive,
  **`enum class`** für feste Mengen. `when` über sealed/enum **ohne `else`**, damit der Compiler Exhaustiveness prüft.
- **Null-Safety ohne `!!`.** Stattdessen `?.`, `?:`, `let`, `requireNotNull(x) { "Grund" }` (nur bei echten Invarianten). Ausnahme: Einstiegspunkte, wo das Fehlen ein Programmierfehler ist
  (z. B. `document.body!!` im Web-Main), mit Kommentar.
- **Kein Wildcard-Import** (`import x.*` ist verboten – ältere Dateien in `shared/model` und `ChargeShareApi.kt` sind Altlasten und werden bei Berührung bereinigt).
- Extension-Funktionen **maßvoll**: nur wenn sie eine klare, allgemeine Bedeutung haben und im selben Paket/Feature gefunden werden. Keine versteckte Geschäftslogik in Extensions.
- Benannte Argumente bei mehr als zwei Parametern oder gleichartigen Typen (`Wallbox(name = …, powerKw = …)`). Trailing Commas bei mehrzeiligen Listen.
- Keine Platform Types leaken lassen, Sichtbarkeit so klein wie möglich (`private`, `internal`); öffentliche API nur, was andere Module brauchen.
- **Explicit API Mode ist aktuell in keinem Modul aktiv.** Solange das so ist, gilt: Rückgabetypen öffentlicher Funktionen trotzdem explizit schreiben.
  Falls Explicit API Mode für `shared` eingeführt wird (empfohlen), hier eintragen.
- Einheitlicher Sprachstil: **Identifier und Dateinamen Englisch**, **KDoc und Kommentare Deutsch** (so ist das Repo gewachsen – dabei bleibt es),
  **UI-Texte Deutsch** (Abschnitt 9).

Coroutines und Flow:

- **Structured Concurrency.** Coroutinen starten nur in einem Scope mit Lebenszyklus: `viewModelScope`, `rememberCoroutineScope`, `coroutineScope { }`.
  **Kein `GlobalScope`**, kein `runBlocking` im Produktionscode.
- **Dispatcher werden injiziert** (Konstruktorparameter), nie hartkodiert. ViewModels nutzen `viewModelScope` (`Dispatchers.Main`); Tests ersetzen ihn über
  `TestMain.install()`/`uninstall()` (`composeApp/src/commonTest/.../testing`, setzt `Dispatchers.setMain(UnconfinedTestDispatcher())`). Zeit kommt als `Clock` und `TimeZone` in den Konstruktor.
- **`CancellationException` nie schlucken.** Wer `catch (e: Exception)` oder `runCatching` nutzt, muss `CancellationException` erneut werfen.
  Dafür gibt es in `data/` den Helfer `runCatchingOutcome { … }`, der das korrekt macht – bitte nutzen statt eigener `runCatching`-Blöcke um `suspend`-Aufrufe.
- `StateFlow` für Zustand, `Flow` für Ströme. `stateIn(…, SharingStarted.WhileSubscribed(5_000), …)` für abgeleitete Zustände. Kein `collect` in Composables
  ohne `collectAsState()`/`collectAsStateWithLifecycle`.

Serialisierung und Zeit:

- **kotlinx.serialization** für alle JSON-Modelle (`@Serializable`, `@SerialName`). Kein manuelles JSON-Zusammenbauen.
- Zeitpunkte als `kotlin.time.Instant` (im API-Vertrag RFC 3339/UTC); Zeitzonen- und Datumslogik mit **kotlinx-datetime** (`TimeZone`, `LocalDate`, `LocalTime`).
  Anzeige immer in der Zeitzone des Geräts (`TimeZone.currentSystemDefault()`), Berechnung in UTC. Uhr/Zeitzone in testbarem Code injizieren.

Fehlerbehandlung:

- Erwartbare Fehler (Netz weg, falsches Passwort, Slot vergeben) sind **Werte**, keine Exceptions im Programmfluss:
  `Outcome<T>` = `Success(value)` | `Failure(error: AppError)`, `AppError` ist ein `sealed interface`
  (`Network`, `Unauthorized`, `Validation`, `Conflict`, `NotFound`, `Forbidden`, `Server`, `Unknown`).
- Exceptions nur für Programmierfehler und an Systemgrenzen (der Ktor-Client wirft `ApiException`; die Repositories fangen sie genau einmal ab).
- Jeder `AppError` hat in der UI eine **verständliche deutsche Meldung** – nie Stacktrace oder Server-Rohtext anzeigen.

## 8. Compose-Regeln

- **State Hoisting:** Composables sind **zustandslos** („stateless“): sie bekommen Zustand als Parameter und melden Ereignisse per Lambda (`onClick`).
  Zustand wohnt im ViewModel; rein visueller Zustand (z. B. Dropdown offen) darf mit `remember` lokal bleiben.
  Pro Screen gibt es ein dünnes `XyzRoute(viewModel)` (holt State, verbindet Events) und ein zustandsloses `XyzScreen(state, onEvent…)`.
- **`modifier: Modifier = Modifier` ist der erste optionale Parameter** jedes öffentlichen Composables und wird an das Wurzel-Element weitergereicht.
- **Material 3** (`androidx.compose.material3` bzw. die CMP-Variante) und **Theme-Tokens** statt Hex-Werten im Screen-Code:
  Farben nur über `MaterialTheme.colorScheme`/`ChargeShareColors` (Hell/Dunkel, siehe Abschnitt 12; nie `Color.White` o. ä. oder zwischengespeicherte Farbwerte), Typografie über `MaterialTheme.typography`, Abstände über benannte Konstanten (`Spacing`).
- **Previews:** `@Preview` nur dort, wo die Compose-Preview-Tooling-Abhängigkeit vorhanden ist (Android Studio) – Screens so schreiben, dass sie mit festem
  `UiState` ohne ViewModel renderbar sind (das ist durch das Stateless-Muster gegeben).
- **Barrierefreiheit:** jedes nicht rein dekorative Icon/Bild hat eine `contentDescription` (deutsch, aus `Strings`); dekorative: `null`.
  Klickflächen ≥ 48 dp. Information **nie nur über Farbe** transportieren (z. B. Status immer auch als Text). Kontrast mindestens WCAG AA.
- **Textskalierung (Dynamic Type):** Schriftgrößen ausschließlich in `sp` über `MaterialTheme.typography`, Layouts dürfen bei 200 % Skalierung nicht abschneiden
  (keine festen Höhen für Textcontainer, `Modifier.heightIn`, scrollbare Spalten).
- **Listen:** `LazyColumn` mit stabilen `key`s (`id`). Teure Berechnungen nicht im Composable-Body (→ ViewModel oder `remember`).
- Composables sind klein (Richtwert < 60 Zeilen); wiederverwendbare Teile nach `ui/components`.

## 9. Lesbarkeit für Menschen

- **Sprechende Namen** ausgeschrieben (`estimatedKwh`, nicht `eKwh`). Booleans lesen sich als Aussage (`isOnline`, `hasError`).
- **Kleine Funktionen** mit einer Aufgabe (Richtwert < 30 Zeilen), flache Verschachtelung (frühe `return`s statt tiefer `if`-Pyramiden).
- **KDoc für jede öffentliche Klasse/Funktion** in `shared` und `data`/`presentation`: *was* sie tut und *welche Fehler* sie meldet.
- **Kommentare erklären das Warum**, nicht das Was (Fachregel, Vertragsdetail, Workaround). Kein auskommentierter Code.
- **Keine Magic Numbers:** benannte Konstanten (`const val` / `private val`) mit Einheit im Namen (`DEFAULT_RADIUS_M`, `SLOT_MINUTES`).
- **Deutsche UI-Texte nie im Composable verstreuen:** alle Texte stehen zentral in `ui/Strings.kt` (Funktionen für Texte mit Platzhaltern).
  Das ist der bewusste Zwischenschritt vor Compose-Multiplatform-Resources (`composeResources/values/strings.xml`); Migration ist erlaubt, sobald Mehrsprachigkeit nötig wird.
- Formatierung deutsch: Dezimalkomma, `€` hinter dem Betrag, 24-Stunden-Zeit (`14:30`), Datum `Mo, 5. Okt.`. Dafür gibt es zentrale Formatter (`ui/Formatters.kt`), keine Ad-hoc-String-Templates.
- Dateien enthalten **eine** zusammengehörige Sache; Dateiname = Hauptklasse. Maximal ca. 400 Zeilen pro Datei.

## 10. Tests

- **kotlin.test** (`@Test`, `assertEquals`, …) in `commonTest`, damit Tests auf allen Targets laufen. Coroutines mit `kotlinx-coroutines-test` (`runTest`).
- **Fakes statt Mocks:** für Repositories handgeschriebene `FakeXyzRepository`-Klassen in `composeApp/src/commonTest/.../testing/Fakes.kt`, keine Mocking-Frameworks.
  Ausführen: `./gradlew :composeApp:desktopTest`.
- **Ktor-Client** wird mit der **`MockEngine`** getestet (`shared/src/commonTest`, `createHttpClient(engine = …)`).
- **Server-Integrationstests** laufen gegen ein **echtes eingebettetes PostgreSQL** (`embedded-postgres`, `TestDb` in `TestSupport.kt`) über `testApplication` – keine H2-Ersatzdatenbank.
- Jedes ViewModel hat Tests für: Initialzustand, Erfolgsfall, Fehlerfall (je `AppError`-Art), Validierung und Ladezustand.
- Neue Funktion ⇒ neuer Test **im selben Commit**. Fehler zuerst per Test reproduzieren, dann beheben.
- Tests heißen als Aussage in `camelCase` (`loginWithWrongPasswordShowsInvalidCredentials`), weil `commonTest` auch für Wasm kompiliert wird; Backtick-Namen nur in JVM-only-Tests (`server/src/test`).

## 11. Sicherheit

- **Keine Secrets im Repo.** Schlüssel (`JWT_SECRET`, `STRIPE_SECRET_KEY`, …) nur per Umgebungsvariable/`.env` (steht in `.gitignore`); `.env.example` enthält nur Platzhalter.
- **Tokens** (Access/Refresh) liegen im `TokenStore`: Android = AES-GCM mit Android-Keystore-Schlüssel, iOS = Keychain,
  Desktop = Datei im Benutzerverzeichnis (nur Besitzer lesbar, **nicht verschlüsselt**), Web = `localStorage` (für XSS angreifbar – bekannte Einschränkung). Nie loggen.
- **Klartext-Traffic (`http://`) nur im Debug-/Dev-Betrieb:** `usesCleartextTraffic="true"` steht im Manifest von `androidApp` nur für `http://10.0.2.2:8080` und muss vor einem Release
  entfernt/auf Debug beschränkt werden. Produktiv ausschließlich `https://`.
- **Stripe:** nur der Publishable Key (`STRIPE_PUBLISHABLE_KEY`, `pk_…`) verlässt den Server (`GET /payments/config`); `AppConfig` lehnt Werte ohne `pk_`-Präfix ab. Secret Key und Webhook-Secret bleiben auf dem Server. Zahlungsdaten (Kartennummern) berührt die App nie – sie liegen im Stripe-PaymentSheet. Apple-Merchant-ID und Google-Pay-Umgebung stehen in der Server-Konfiguration, nicht im App-Code.
- Passwörter nie loggen oder im UI-State halten, länger als nötig (nach dem Absenden leeren). Server speichert bcrypt (Kosten 12) und Refresh-Tokens nur als SHA-256.
- Eingaben im Client validieren (Komfort) **und** im Server (Sicherheit) – der Server ist maßgeblich.

## 12. Design-Tokens

Quelle: `docs/ChargeShare-App-Design.html` (CSS-Variablen), umgesetzt in `ui/Theme.kt`.

| Token | Wert |
|---|---|
| Primär (Tiefgrün) | `#0B6E4F` (hell `#D4EDE5`, Fläche `#E8F5EE`) |
| Akzent (Amber, CTA) | `#F0A500` (hell `#FFF4CC`, dunkel `#C87800`) |
| Fehler | `#D0392B` (hell `#FDECEA`) |
| Eckenradien | 10 / 14 / 20 / 26 dp (sm/md/lg/xl); Chips/Pills und Navigations-Pill vollrund |
| Dunkel | Hintergrund `#141310`, Fläche `#1E1D1A` (2: `#272522`, 3: `#302E2A`), Rand `#363330`, Text `#E8E6E2` / `#A8A5A0`, Primär-Fläche `#0D2B1F` / `#0D3824`, Amber-Fläche `#2D2000` |
| Text gedämpft / inaktiv | `#555250` / `#9E9B98` (`OnSurfaceVariant` / `OnSurfaceMuted`) |
| Schatten | `Elevation.Card` (2 dp, Karten und Chips), `Elevation.Raised` (6 dp, schwebende Elemente) |
| Schrift | Plus Jakarta Sans 400/500/600/700/800 (`ui/Typography.kt`, `chargeShareTypography`) |

Der **Amber-Button** ist die Haupthandlung auf Detail- und Bestätigungsseiten (z. B. „Zeitslot buchen“); der abschließende „Jetzt buchen · Preis“-Button im Buchungsflow ist wie im Mockup ein grüner `PrimaryButton` mit Haken. Der Buchungsflow nutzt `ui/booking/BookingPickers.kt` (Datumsscroller, 3-Spalten-Startzeitraster mit grauen/durchgestrichenen belegten Slots, Dauer-Pills in Amber) und eine Amber-Kostenkarte. Hero-Bereiche sind tiefgrün mit weißer Schrift.

**Hell/Dunkel:** `ChargeShareColors.X` (z. B. `Primary`, `Surface`, `OnSurfaceMuted`) liefert die Farbe des **aktuellen** Themes über `LocalChargeShareColors` und ist nur in Composables nutzbar –
keine Farben in `val`/`const`/`enum`-Konstruktoren zwischenspeichern. `ChargeShareTheme(darkTheme)` setzt zusätzlich das Material-`colorScheme` (inkl. `surfaceContainer*` für Dialoge).
Wichtig: `Primary` ist die **Füllfarbe** (Hero, Button, aktiver Pin, gewählter Chip; immer mit `OnPrimary`), `PrimaryText` die **Text-/Icon-/Rahmenfarbe** auf Flächen – im Dunkeln ist sie hell (`#5ECC8A`), weil `#0B6E4F` auf `#1E1D1A` nur 2,7:1 hat.
Gleiches gilt für `Error`, `OnSurfaceMuted` und `OnAccentLight`; diese Dunkel-Werte weichen bewusst vom Mockup ab. `ThemeTest` prüft die WCAG-Kontraste (4,5:1 für Text). Neue Farbpaare dort ergänzen.
Die gewählte Darstellung (`ThemeMode` Hell/Dunkel/System) hält `ThemeSettings` (im `AppContainer`), gespeichert über `PreferenceStorage`; Auswahl im Profil.

**Schrift:** Plus Jakarta Sans (SIL Open Font License 1.1) liegt als Compose-Multiplatform-Ressource in `composeApp/src/commonMain/composeResources/font/`
(Dateinamen nur `a-z0-9_`), der Lizenztext in `composeResources/files/plus_jakarta_sans_ofl.txt`. Die Ressourcen-Klasse heißt `app.chargeshare.resources.Res`
(`compose.resources.packageOfResClass` in `composeApp/build.gradle.kts`; auf Android braucht der KMP-Library-Plugin dafür `androidResources { enable = true }`).
Texte nutzen immer `MaterialTheme.typography` (Stile in `Typography.kt`), nie eine eigene `fontSize`.

**Debug-Angaben:** `AppContainer(showDebugInfo = …)` ist nur in Debug-Builds `true` (Android: `BuildConfig.DEBUG`, Desktop-Vorschau: immer); die UI zeigt dann z. B. die API-Adresse im Profil.
In Release-Builds, im Web und auf iOS (Standard `false`) erscheinen solche Zeilen nicht.

**Wiederverwendbare Design-Komponenten** (`ui/components`): `AppCard`/`SurfaceCard` (Karte mit Radius 20 dp, Schatten, Rand), `AppFilterChip`/`ChoiceChips` (vollrunde Filter-Pill,
gewählt tiefgrün), `InfoPill` (kleines Label mit Füllung), `OutlineChip` (Info-Label mit Rand und Icon, Detailseite), `FavoriteButton` (Herz; Zustand teilt sich der `FavoritesViewModel` pro Nutzer, optimistisch mit Rücknahme bei Fehler), `AppNavigationBar` (untere Navigation: aktiver Tab grün auf hellgrüner Pill, inaktiv grau), `MenuGroup`/`MenuItem` (gruppierte Liste mit farbigen Icons, `MenuIconStyle`). Den Material-`FilterChip`/`NavigationBar`/`Card`
nicht direkt verwenden, damit das Design an einer Stelle gepflegt wird.

## 13. Recht und Datenschutz (Kurzform – Details im Konzept, Kap. 9)

- **Eichrecht (MessEG):** entgeltliche Stromabgabe nur mit MID-geeichtem Zähler. Die UI zeigt „MID-geeicht“ (`isMetered`) und Hosts müssen den Zähler angeben.
- **Standort nur mit Einwilligung (DSGVO):** der Gerätestandort wird erst nach ausdrücklicher Zustimmung und mit Erklärung des Zwecks abgefragt; ohne Zustimmung
  funktioniert die Suche über einen Standardstandort/Ortsangabe. Standort nie speichern oder protokollieren.
- **Datensparsamkeit:** nur für den Vertrag nötige Daten erheben; Konto-Löschung (`DELETE /users/me`) muss in der App erreichbar sein.
- Datenschutzerklärung/Impressum sind vor einem Store-Release nötig (noch nicht vorhanden).
- Die rechtlichen Hinweise im Konzept sind **keine Rechtsberatung**.

## 14. Git-Regeln

- **Branch-Workflow (verbindlich):** `main` enthält nur Releases, `develop` ist der Integrationszweig. Niemand committet direkt auf `main` oder `develop`. Jede Änderung entsteht auf einem Branch von `develop` (`feat/<thema>`, `fix/<thema>`, `docs/<thema>`, `chore/<thema>`) und geht per **Pull Request nach `develop`** (Squash-Merge). Erst nach grüner CI und Review wird gemerged. Releases: PR von `develop` nach `main`.
- Ein PR hat einen Zweck, ist klein und folgt `.github/pull_request_template.md`. Der Review-Bot prüft jeden PR; seine Hinweise werden bearbeitet oder begründet abgelehnt.
- Solange kein Remote festgelegt ist, gilt derselbe Ablauf lokal mit Branches; **nichts pushen** ohne Remote-Freigabe des Nutzers.
- Commit-Identität auf der Box: `git -c user.name="ChargeShare" -c user.email="dev@chargeshare.local" commit …`
- **Kleine, thematisch geschlossene Commits**, jeder baut und besteht seine Tests.
- Nachrichtenformat **Conventional-Commit-artig** mit Bereich, Beschreibung deutsch oder englisch (wie bisher gemischt – bevorzugt deutsch):
  `feat(app): Buchungsflow`, `fix(server): …`, `docs: …`, `test(app): …`, `build: …`, `refactor(shared): …`.
- Keine Build-Artefakte (`build/`, `.gradle/`, `.kotlin/`, `server/.local-pg/`) committen – `.gitignore` beachten.
- Keine Force-Pushes, kein History-Umschreiben bereits veröffentlichter Commits.

## 15. Definition of Done

Ein Auftrag ist erst fertig, wenn **alle** Punkte gelten:

- [ ] Verhalten entspricht Konzept/Design (`docs/`) und diesem Dokument.
- [ ] Neue/geänderte Logik hat Tests (ViewModel: Fakes; Netz: MockEngine; Server: echtes PostgreSQL) – und sie sind grün.
- [ ] `./gradlew :shared:jvmTest :server:build :composeApp:desktopTest :composeApp:desktopJar :web:wasmJsBrowserDistribution :androidApp:assembleDebug` ist grün.
- [ ] Keine neuen Compiler-Warnungen, kein `!!`, keine Wildcard-Imports, kein `GlobalScope`, keine Magic Numbers.
- [ ] Öffentliche API hat KDoc; UI-Texte stehen in `Strings.kt`; Icons haben `contentDescription`.
- [ ] Fehlerfälle sind in der UI sichtbar und verständlich (kein stiller Fehlschlag, kein Rohtext).
- [ ] Keine Secrets, keine Build-Artefakte im Commit; `README.md`/`AGENTS.md` bei geändertem Verhalten aktualisiert.
- [ ] Was **nicht** getestet/umgesetzt ist, steht ehrlich in der Abschlussmeldung und unter „Bekannte Lücken“.

## 16. Bekannte Lücken (Stand: Oktober 2026)

- **Apple Sign In** (`POST /auth/apple`) und **Stripe** (PaymentIntent, Connect, Webhook) sind **nicht gegen echte Apple-/Stripe-Konten getestet**.
  Die App hat seit dem Zahlungs-PR eine Zahlungsoberfläche (Stripe-PaymentSheet mit Google Pay / Apple Pay, siehe README „Zahlung“), die aber **nie mit echten Stripe-Testschlüsseln durchgespielt** wurde. Nur die Fake-Zahlung (Dev) ist end-to-end getestet.
- **iOS** lässt sich auf Linux nur zu Klibs kompilieren, **nicht linken** und nicht in Xcode/Simulator testen – das geht nur auf macOS. Der Keychain-`TokenStore` ist dort ungetestet.
- **Rate-Limiting:** nur für `POST /auth/register|login|apple` (gemeinsamer Zähler je Client-Adresse, Standard 10/Minute, `AUTH_RATE_LIMIT_PER_WINDOW`/`_WINDOW_SECONDS`, `0` = aus; `429` + `Retry-After`, Code `RATE_LIMITED`). Zähler im Speicher je Instanz (kein Cluster, weg beim Neustart); der Go-Stand hatte zusätzlich 120/Minute für die übrige API – nicht portiert. Hinter einem Reverse-Proxy `TRUST_PROXY_HEADERS=true` setzen (liest den letzten `X-Forwarded-For`-Eintrag), sonst teilen sich alle Nutzer eine Adresse. Server-Tests nutzen `testConfig` mit abgeschaltetem Limit.
- **Karte:** MapCompose-MP mit OSM-Kacheln, aktiv auf **Android, Desktop und iOS**; jeder Pin ist ein Preisschild („0,34 €“, aktiv = tiefgrün, `WallboxMap`), unten liegt das Fenster „N Stationen in der Nähe“ (`StationSheet`: Leistung, Stecker, Entfernung, Preis; Tipp auf eine Zeile öffnet das Detail, Tipp auf einen Pin markiert die Wallbox und scrollt sie ins Fenster); Schnellfilter-Pills „Alle / Typ 2 / 11 kW+ / Jetzt frei / Mehr Filter“ gelten für Karte und Liste; auf **Web** zeigt die Suche nur die Liste (`isMapSupported = false`, Freischaltung erst nach Test im Browser). Die iOS-Karte ist auf der Box nicht im Simulator geprüft (kein Xcode) – manueller Check auf dem Mac. Die OSM-Server sind nur für moderate Nutzung gedacht – vor Release eigenen Kachelserver/Anbieter nutzen. Keine Cluster bei vielen Pins, kein Offline-Kartenmaterial, keine UI-Tests der Karte. Geräte-Standort ist noch nicht angebunden (Standardstandort Bremen); die Freitext-Suche filtert nur die geladenen Ergebnisse (Backend hat keine Textsuche).
- **Zahlung:** Apple Pay (Swift-Brücke, SPM-Paket, Entitlements) ist nicht auf einem Mac gebaut oder getestet und braucht Merchant-ID + bezahltes Apple-Developer-Konto; Google Pay braucht für Produktion die Freischaltung in der Google Pay & Wallet Console. Unbezahlte `pending`-Buchungen laufen nach `PENDING_BOOKING_TTL_MINUTES` (Standard 15) automatisch ab (`PendingBookingSweeper`, schließt den PaymentIntent, bestätigt statt storniert bei zwischenzeitlicher Zahlung); der manuelle Storno schließt den offenen PaymentIntent ebenfalls, wird aber ein Intent zwischen Abfrage und Storno noch bezahlt, erfolgt dort keine automatische Erstattung. „Jetzt bezahlen“ in der Buchungsliste (Android/iOS) ist nur gegen Fake-/Skript-Provider getestet, nicht gegen ein echtes Stripe-Konto. Rückzahlungen/Teilabrechnung nach kWh fehlen.
- **Bewertungen:** die App bietet „Bewerten“ (Sterne + Kommentar) in der Buchungsliste für `completed`-Buchungen. Eine Buchung wird aber nur durch einen echten OCPP-Ladevorgang `completed` (kein Zeit-basierter Abschluss, keine Dev-Abkürzung) – der Dialog ist daher nur per ViewModel-Tests und serverseitig getestet, nicht in der laufenden App durchgespielt.
- **Detailseite:** Hero mit Modell-Badge (Hersteller + Modell aus den Wallbox-Daten, sonst nur die Leistung), Host-Karte und Herz. Der Mockup-Hinweis „ca. 0,28 €/km“ fehlt bewusst (er bräuchte einen angenommenen Fahrzeugverbrauch). Das Herz gibt es in Detail und Liste, **nicht** im Stationsfenster der Karte; einen Filter „nur Favoriten“ und ein Host-Profil hinter der Host-Karte gibt es nicht. Der Chip „9–20 Uhr“ erscheint nur, wenn alle Tage dasselbe eine Zeitfenster haben. Keine automatisierten UI-Tests der neuen Composables.
- **Auszahlungs-Onboarding** (Host-Dashboard, `PayoutViewModel`/`PayoutStatusCard`): Browser-Start über `UrlOpener` (Android `ACTION_VIEW`, Desktop `java.awt.Desktop`, Web neuer Tab, iOS `openURL`), Rückkehr per Deep Link über `DeepLinkInbox` (im `AppContainer`; Android `MainActivity` mit `launchMode=singleTask` und `onNewIntent`). **iOS:** URL-Schema und Swift-Brücke für den Deep Link fehlen noch; die Stripe-Rückkehr im Browser des Emulators/Geräts ist nur gegen Stripe-Testkonto manuell prüfbar.
- **Passwort zurücksetzen** (`PasswordResetService`, Migration V5): der Server hat **keinen echten Mailversand**, nur `LoggingMailer` (Dev: Link im Log; in Produktion wird er nicht geloggt). Vor dem Livegang einen `Mailer` mit SMTP/Provider ergänzen (`Mailer.from(config)`). App: „Passwort vergessen?“ im Login, Deep Link `chargeshare://reset-password?token=…` öffnet „Neues Passwort“ (`SignedOutContent`); iOS-Deep-Link nicht angebunden, das Token lässt sich dort von Hand eintragen. Angemeldet wird ein Reset-Link verworfen.
- **Quittung (PDF):** `GET /bookings/{id}/receipt` nur für `completed`-Buchungen des Fahrers, also praktisch erst nach einem echten OCPP-Ladevorgang; getestet mit per SQL gesetztem Status. Keine Rechnung mit Umsatzsteuer (steht im PDF). App: `ReceiptSharer` (Android Teilen-Dialog über `FileProvider`, Desktop öffnet die Datei, Web lädt herunter, iOS nicht angebunden → Knopf ausgeblendet).
- **Gespeicherte Karten:** Profil → „Zahlungsmethoden“ (`SavedCardsViewModel`), Hinzufügen über SetupIntent im PaymentSheet (nur Android, `PaymentLauncher.supportsCardSetup`); `StripePaymentLauncher` hängt bei Buchungen eine Customer Session an. Server und App sind **nur mit Testdoubles getestet, nie gegen Stripe**; iOS hat keinen `saveCard`.
- **Fotos:** nur der Host lädt im Bearbeiten-Formular hoch (`PhotosSection`, `PhotoPicker`: Android Photo Picker, Desktop Dateidialog; Web und iOS ohne Auswahl, dort ist der Knopf ausgeblendet). Der Server kodiert neu und entfernt EXIF/GPS, wendet aber die EXIF-Drehung **nicht** an; nur der Android-Picker dreht selbst. Kein WebP/HEIC. `ImageLoader` hält dekodierte Bitmaps nach Pixelbudget im Speicher, ohne Festplatten-Cache. Der Foto-Ordner des Servers (`PHOTO_STORAGE_DIR`) braucht ein dauerhaftes Volume. Fotos wirken sofort auf dem Server, unabhängig vom „Speichern“ des Formulars.
- **Geocoding:** `POST /geocode` über Nominatim mit Rate-Limit, Cache und User-Agent (`GEOCODER_USER_AGENT` in Produktion mit Kontakt setzen). Die Koordinaten lassen sich weiter von Hand eintragen; die Tests nutzen `FakeGeocoder`, nie das echte Netz.
- **Noch nicht in der UI:** Apple Sign In, Wallbox löschen.
- **Tests:** es gibt ViewModel-/Daten-Tests (89 in `composeApp`), aber **keine automatisierten UI-/Composable-Tests** und keine Tests auf echtem Gerät. Der Desktop-Build wurde manuell gegen das lokale Backend geprüft (Login, Liste, Detail, Buchung, Host-Wechsel).
- **Splash:** Android nutzt die SplashScreen-API (`androidx.core:core-splashscreen`, Startthema `Theme.ChargeShare.Starting`, Farben in `res/values(-night)/colors.xml`), danach läuft in allen Plattformen `SplashOverlay` (`ui/Splash.kt`, etwa eine Sekunde, wartet auf die Sitzungswiederherstellung). Die Farben stehen doppelt (XML und `SplashColors`) und müssen zusammen geändert werden. Es gibt noch kein App-Icon und keine Reduce-Motion-Behandlung; iOS hat keinen nativen Launch-Screen mit Blitz (nur die Compose-Animation). Die Animation selbst ist nicht automatisiert getestet, nur die Entscheidungslogik.
- **Einführung (Onboarding):** vier Seiten (`OnboardingPage`: Finden, Buchen, Laden, Host werden) beim ersten Start, überspringbar. `OnboardingSettings` im `AppContainer` speichert das Flag `onboarding_seen` **lokal pro Gerät** im `PreferenceStorage` (nicht pro Konto, nicht auf dem Server); im Profil → „Einführung ansehen“ zeigt sie erneut, ohne das Flag zu ändern. Sie erscheint vor der Anmeldung, über der Sitzungsprüfung. Die Grafiken sind Stimmungsbilder in `composeResources/drawable/` (`finden`/`buchen`/`laden`/`host`); der Leerzustand der Suche nutzt `leer`. Die UI (Pager, Punkte) ist nicht automatisiert getestet, nur `OnboardingSettings`.
- **Zurück-Taste:** `BackHandler` ist in Compose Multiplatform 1.12 als veraltet markiert (Nachfolger `NavigationEventHandler`); Migration offen.
- **Host-Wallbox-Liste:** das Backend hat keinen Endpunkt „meine Wallboxen“; die App filtert die Suchergebnisse nach `owner_id` (max. 100).
- **Verfügbarkeit** wird vom Server in **UTC** gespeichert/berechnet (feste Wochentage und Uhrzeiten, keine Zeitzone/Sommerzeit). Die App zeigt und erfasst sie in **deutscher Zeit** (`WALLBOX_ZONE` = Europe/Berlin; Umrechnung in `data/AvailabilityTime.kt`, Fenster über Mitternacht werden geteilt, Ende `24:00` ist erlaubt). Es gilt der UTC-Versatz von „jetzt“: nach der Zeitumstellung verschieben sich die Ortszeiten um eine Stunde, bis der Host den Plan neu speichert (Hinweis in der UI). Sauber lösbar nur mit gespeicherter Zeitzone je Wallbox im Backend. Der Dev-Seed (06:00–22:00 UTC) erscheint daher als 07:00/08:00–23:00/24:00 Uhr.
- Offen im Backend: Zahlung nach OCPP-Abschluss auf tatsächliche kWh abrechnen, RemoteStart/Stop, Bewerten/Einnahmen erst nach echtem OCPP-Ladevorgang durchspielbar.
- CI: GitHub Actions (`ci.yml`) für shared/server/web/android läuft auf dem **Self-hosted-Runner** `bazzite-laptop` (Label `chargeshare`, systemd-User-Dienst `github-runner`, Details im README „CI und Self-hosted-Runner“). Ist der Laptop aus, hängt die CI; Fallback ohne Code-Änderung: `gh variable set CI_RUNS_ON --body '"ubuntu-latest"'` (zurück: `gh variable delete CI_RUNS_ON`). macOS/iOS-CI fehlt noch.
