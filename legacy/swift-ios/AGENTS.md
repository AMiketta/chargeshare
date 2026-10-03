# AGENTS.md — ChargeShare iOS

Kontext für AI-Coding-Assistenten (Cursor, GitHub Copilot, Claude, etc.)

---

## Projektübersicht

**ChargeShare** ist ein iOS MVP (SwiftUI, iOS 17+) für ein Peer-to-Peer Wallbox-Sharing-Netzwerk.  
Fahrer finden und buchen private Ladestationen, Hosts verwalten ihre Wallboxen und sehen Einnahmen.

**Bundle ID:** `de.chargeschare.app`  
**Backend:** Go REST API auf `http://localhost:8080/api/v1`  
**Authentifizierung:** JWT (Bearer Token) + Apple Sign In

---

## Architektur: Clean Architecture

```
App Layer        → Entry Point, Navigation Shell
Core Layer       → Netzwerk, Auth, Location, Extensions (wiederverwendbar)
Domain Layer     → Pure Swift Models (kein Framework-Import außer Foundation)
Data Layer       → DTOs, Repositories (API ↔ Domain Mapping)
Feature Layer    → Feature-Module mit View + ViewModel
```

### Schichtenregeln

- **Domain** darf nicht von Data, Core oder Features abhängen
- **Data** darf nur Domain und Core importieren
- **Features** dürfen Data und Domain importieren, aber nicht andere Features
- **Keine Circular Imports**

---

## Swift / SwiftUI Konventionen

### @Observable (PFLICHT — kein ObservableObject)

```swift
// RICHTIG
@Observable
final class MapViewModel {
    var wallboxes: [Wallbox] = []
}

// FALSCH — nicht verwenden
class MapViewModel: ObservableObject {
    @Published var wallboxes: [Wallbox] = []
}
```

### ViewModels in Views

```swift
struct MapView: View {
    @State private var viewModel = MapViewModel()  // lokale Instanz
    @Environment(AuthManager.self) var authManager // Singleton via Environment
}
```

### async/await (kein DispatchQueue, kein Combine)

```swift
// RICHTIG
.task {
    await viewModel.load()
}

// FALSCH
.onAppear {
    DispatchQueue.main.async { viewModel.load() }
}
```

### NavigationStack (kein NavigationView)

```swift
NavigationStack {
    List(items) { item in
        NavigationLink(value: item) {
            ItemRowView(item: item)
        }
    }
    .navigationDestination(for: Item.self) { item in
        ItemDetailView(item: item)
    }
}
```

### Error Handling

```swift
// NetworkError als enum in Core/Network/NetworkError.swift
// Views zeigen Alerts via .alert(isPresented:) mit errorMessage-Binding
var errorMessage: String?
var showError: Bool { errorMessage != nil }
```

---

## Naming Conventions

| Typ | Suffix | Beispiel |
|-----|--------|---------|
| View | `View` | `MapView`, `WallboxDetailView` |
| ViewModel | `ViewModel` | `MapViewModel`, `BookingViewModel` |
| Repository | `Repository` | `WallboxRepository`, `BookingRepository` |
| Manager (Singleton) | `Manager` | `AuthManager`, `LocationManager` |
| DTO | keine / `DTO` | `AuthResponse`, `CreateWallboxRequest` |
| Component | frei | `RatingView`, `PriceLabel`, `StationRowView` |

---

## Dateistruktur und Zuständigkeiten

### App/
- `ChargeShareApp.swift` — `@main`, injiziert AuthManager + LocationManager als `@Environment`
- `RootView.swift` — Branch: `isAuthenticated ? MainTabView : AuthView`
- `MainTabView.swift` — TabView mit Map, Buchungen, Host (konditionell), Profil

### Core/Network/
- `NetworkError.swift` — Fehler-Enum: `.unauthorized`, `.notFound`, `.serverError`, `.decodingError`, `.custom`
- `Endpoint.swift` — Struct mit path/method/queryItems/body, statische Factory-Methoden
- `APIClient.swift` — `@Observable`, `URLSession` async/await, Token-Injektion, Decode

### Core/Auth/
- `KeychainHelper.swift` — Keychain CRUD für Strings (accessToken, refreshToken, userID)
- `AuthManager.swift` — `@MainActor @Observable`, Token-Management, currentUser

### Core/Location/
- `LocationManager.swift` — `@MainActor @Observable`, `CLLocationManagerDelegate`

### Core/Extensions/
- `Color+App.swift` — `Color.csGreen`, `Color.csAmber`, `Color.csGreenLight`
- `Date+Formatting.swift` — Formatierungshilfen (relative, short, time)
- `View+Extensions.swift` — `loadingOverlay`, `errorAlert`, `cardStyle`

### Domain/Models/
- `User.swift` — User, UserRole
- `Wallbox.swift` — Wallbox, ConnectorType, WeeklyAvailability, DaySchedule
- `Booking.swift` — Booking, BookingStatus
- `Review.swift` — Review

### Data/DTOs/
- `APIModels.swift` — AuthResponse, CreateWallboxRequest, CreateBookingRequest, alle API-Request/Response-Typen

### Data/Repositories/
- `AuthRepository.swift` — login, register, loginWithApple, refreshToken, me
- `WallboxRepository.swift` — nearby, detail, slots, reviews, toggleOnline, create
- `BookingRepository.swift` — myBookings, create, cancel, hostBookings, hostEarnings

### Features/Auth/
- `AuthViewModel.swift` — Shared ViewModel für Login + Register
- `AuthView.swift` — Branded Fullscreen (Apple Sign In + E-Mail-Optionen)
- `LoginView.swift` — E-Mail + Passwort Formular
- `RegisterView.swift` — Registrierungsformular mit Validierung

### Features/Map/
- `MapViewModel.swift` — Wallbox-Loading, Filter, Selection, CameraPosition
- `MapView.swift` — iOS 17 Map API mit Annotations, Search, Bottom Sheet
- `StationRowView.swift` — Compact Row für Bottom Sheet Liste

### Features/WallboxDetail/
- `WallboxDetailViewModel.swift` — Reviews laden
- `WallboxDetailView.swift` — Hero, Info-Chips, Host-Section, Buchungs-Button

### Features/Booking/
- `BookingViewModel.swift` — Slot-Loading, Buchungslogik, Kostenberechnung
- `BookingView.swift` — Datum/Zeit/Dauer-Auswahl, Kostenübersicht
- `BookingConfirmationView.swift` — Erfolgsscreen mit Animation

### Features/Host/
- `HostDashboardViewModel.swift` — Parallel-Load: earnings + bookings + wallboxes
- `HostDashboardView.swift` — Earnings Card, Wallbox-Toggle, Swift Charts, Session-Liste
- `AddWallboxView.swift` — Form für neue Wallbox

### Features/Profile/
- `ProfileViewModel.swift` — Nutzerstatistiken
- `ProfileView.swift` — Avatar, Stats, Navigation zu Host-Bereichen

### Features/Bookings/
- `BookingsListView.swift` — Buchungen nach Status gruppiert
- `BookingRowView.swift` — Compact Row mit Status-Badge

### Features/Shared/
- `Components/RatingView.swift` — 5-Sterne mit SF Symbols
- `Components/LoadingOverlay.swift` — ViewModifier mit ProgressView + Blur
- `Components/EmptyStateView.swift` — Leerer Zustand mit Icon + CTA
- `Components/PriceLabel.swift` — Formatierter Preis in verschiedenen Größen
- `Styles/ButtonStyles.swift` — Primary, Secondary, Destructive ButtonStyles

---

## API-Endpunkte Referenz

**Base URL:** `http://localhost:8080/api/v1`

### Auth
| Method | Path | Auth | Beschreibung |
|--------|------|------|-------------|
| POST | `/auth/login` | ❌ | E-Mail + Passwort Login |
| POST | `/auth/register` | ❌ | Registrierung |
| POST | `/auth/apple` | ❌ | Apple Sign In |
| POST | `/auth/refresh` | ❌ | Token Refresh |
| GET | `/auth/me` | ✅ | Aktueller Nutzer |
| PUT | `/auth/me` | ✅ | Profil aktualisieren |

### Wallboxen
| Method | Path | Auth | Beschreibung |
|--------|------|------|-------------|
| GET | `/wallboxes` | ❌ | Suche (lat, lng, radius, connector, minPower, maxPrice) |
| GET | `/wallboxes/:id` | ❌ | Detail |
| GET | `/wallboxes/:id/slots` | ✅ | Verfügbare Zeitslots (date query param) |
| GET | `/wallboxes/:id/reviews` | ❌ | Bewertungen |
| POST | `/wallboxes` | ✅ | Neue Wallbox erstellen |
| PATCH | `/wallboxes/:id/online` | ✅ | Online/Offline toggle |
| POST | `/wallboxes/:id/reviews` | ✅ | Bewertung erstellen |

### Buchungen
| Method | Path | Auth | Beschreibung |
|--------|------|------|-------------|
| GET | `/bookings` | ✅ | Meine Buchungen (Driver) |
| POST | `/bookings` | ✅ | Buchung erstellen |
| DELETE | `/bookings/:id` | ✅ | Stornieren |
| GET | `/host/bookings` | ✅ | Buchungen als Host |
| GET | `/host/earnings` | ✅ | Einnahmen (from, to query params) |

### Request/Response Formate

**Auth Request:**
```json
{ "email": "user@example.com", "password": "secret123" }
```

**Auth Response:**
```json
{
  "access_token": "eyJ...",
  "refresh_token": "eyJ...",
  "user": { "id": "...", "email": "...", "name": "...", "role": "driver" }
}
```

**Wallbox Search Response:**
```json
[{ "id": "...", "name": "...", "lat": 48.1, "lng": 11.5, "price_per_kwh": "0.34", ... }]
```

**Create Booking Request:**
```json
{
  "wallbox_id": "uuid",
  "start_time": "2024-01-15T10:00:00Z",
  "end_time": "2024-01-15T12:00:00Z"
}
```

**Error Response:**
```json
{ "error": "Unauthorized", "code": "UNAUTHORIZED" }
```

---

## Farb-System

| Name | Hex | Asset | Verwendung |
|------|-----|-------|-----------|
| ChargeShare Green | #0B6E4F | `ChargeShareGreen` | Primary, CTAs, Accent |
| ChargeShare Amber | #F0A500 | `ChargeShareAmber` | Selected Duration, Warnings |
| csGreenLight | opacity 12% | computed | Chip Backgrounds, Highlights |

---

## Wichtige Patterns

### Optimistic UI (Toggle)
```swift
func toggleWallbox(_ wallbox: Wallbox) async {
    // Sofort local updaten
    if let index = myWallboxes.firstIndex(where: { $0.id == wallbox.id }) {
        myWallboxes[index].isOnline.toggle()
    }
    do {
        let updated = try await WallboxRepository().toggleOnline(id: wallbox.id, online: !wallbox.isOnline)
        if let index = myWallboxes.firstIndex(where: { $0.id == wallbox.id }) {
            myWallboxes[index] = updated
        }
    } catch {
        // Revert on failure
        if let index = myWallboxes.firstIndex(where: { $0.id == wallbox.id }) {
            myWallboxes[index].isOnline = wallbox.isOnline
        }
    }
}
```

### Parallele Loads
```swift
func load() async {
    isLoading = true
    defer { isLoading = false }
    async let earningsTask = BookingRepository().hostEarnings(from: monthStart, to: Date.now)
    async let bookingsTask = BookingRepository().hostBookings()
    (earnings, recentBookings) = try await (earningsTask, bookingsTask)
}
```

### Map Camera Position
```swift
// Fallback auf Deutschland-Mitte wenn kein Location-Zugriff
var cameraPosition: MapCameraPosition = .region(
    MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 51.1657, longitude: 10.4515),
        span: MKCoordinateSpan(latitudeDelta: 5, longitudeDelta: 5)
    )
)
```
