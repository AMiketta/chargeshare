# ChargeShare iOS

ChargeShare ist eine iOS-App (SwiftUI, iOS 17+) zum Teilen und Buchen privater Wallboxen.

## Voraussetzungen

- **Xcode 16+** — [Mac App Store](https://apps.apple.com/de/app/xcode/id497799835)
- **XcodeGen** — `brew install xcodegen`
- **SwiftLint** (optional) — `brew install swiftlint`
- **Docker** — für das Go-Backend

## Setup

### 1. Xcodeproj generieren

```bash
make generate
```

### 2. In Xcode öffnen

```bash
make open
```

### 3. Developer Team setzen

Öffne `project.yml` und ersetze `CHARGESHARETEAM` durch deine Apple Developer Team-ID:

```yaml
settings:
  base:
    DEVELOPMENT_TEAM: DEIN_TEAM_ID
```

Führe danach erneut `make generate` aus.

### 4. Backend starten

```bash
cd ../chargeschare-backend
docker-compose up -d
```

Das Backend läuft auf `http://localhost:8080/api/v1`.

## Architektur

```
ChargeShare/
├── App/          → Entry Point, RootView, MainTabView
├── Core/         → Network, Auth, Location, Extensions
├── Domain/       → Reine Swift-Modelle (kein Framework)
├── Data/         → DTOs, Repositories (API-Mapping)
└── Features/     → Feature-Module (Auth, Map, Booking, Host, Profile)
```

**Patterns:**
- **Clean Architecture** — strenge Schichttrennung
- **@Observable** — kein ObservableObject / Combine
- **async/await** — kein DispatchQueue
- **@Environment** — Dependency Injection für Singletons

## Befehle

| Befehl | Beschreibung |
|--------|-------------|
| `make generate` | Generiert `ChargeShare.xcodeproj` aus `project.yml` |
| `make open` | Generiert und öffnet in Xcode |
| `make lint` | SwiftLint (strict mode) |
| `make clean` | Löscht generiertes Projekt und Build-Artefakte |

## Features

- **Karte** — Map mit Wallbox-Pins, Filter (Connector, Power, Preis)
- **Buchung** — Zeitslot-Auswahl, Dauer, Kostenvorschau
- **Host Dashboard** — Einnahmen-Chart (Swift Charts), Wallbox-Toggle, Session-Historie
- **Apple Sign In** — Primäre Authentifizierung
- **Profil** — Statistiken, Verifizierungsstatus, Abmeldung

## Limitierungen (MVP)

- Apple Pay: Mockup (kein echtes Merchant-Account nötig für Entwicklung)
- Push-Benachrichtigungen: Infrastruktur vorhanden, aber kein APNs-Setup
- Offline-Mode: Nicht implementiert
