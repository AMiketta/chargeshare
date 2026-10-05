# Push-Benachrichtigungen (MVP)

Ziel: Host und Fahrer bei wichtigen Buchungs-/Lade-Ereignissen benachrichtigen.
Dieses MVP **versendet noch keine echten FCM/APNs-Nachrichten** – der Server loggt sie
(`LoggingPushNotifier`). Die App kann Gerätetokens registrieren; ohne Firebase/APNs-Setup
bleibt das Token leer.

## Ereignis → Empfänger

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

## API

| Methode | Pfad | Auth | Zweck |
|---|---|---|---|
| `POST` | `/api/v1/devices` | JWT | Token registrieren (`platform`: `android`\|`ios`, `token`) → `204` |
| `DELETE` | `/api/v1/devices` | JWT | Token abmelden (`token`) → `204` |
| `POST` | `/api/v1/bookings/{id}/problem` | JWT (Fahrer) | Problemtext an Host → Push + `204` |

## Server-Verhalten

- Tabelle `push_devices` (Flyway `V6__push_devices.sql`), Upsert per Token.
- `PushNotifier` + Standard `LoggingPushNotifier` (CI/Dev ohne Secrets).
- Hooks in `BookingService` (confirm/cancel) und `OcppHandler` (start/stop).

## App

- Nach Login/`restoreSession` → `PushDeviceRepository.syncRegistration()` (No-Op ohne Token).
- Beim Logout → gespeichertes Token abmelden.
- Android: Token-Quelle ist vorbereitet; FCM-Dienst ist **Stub** (`androidApp/.../push/FcmMessagingService.kt`).
- iOS: Permission/Token-Platzhalter; APNs folgt später.
- Keine gefakten OS-Notifications: ohne Provider sieht man die Ereignisse nur im Server-Log.

## Firebase später aktivieren

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
