package app.chargeshare.android.push

/**
 * Platzhalter für Firebase Cloud Messaging.
 *
 * **Noch nicht aktiv:** ohne `google-services.json` und ohne `firebase-messaging`-Abhängigkeit
 * kompiliert die App absichtlich ohne echten FCM-Dienst (CI bleibt grün).
 *
 * Aktivierung (siehe `README.de.md#push-benachrichtigungen-mvp`):
 * 1. `androidApp/google-services.json` aus der Firebase-Konsole ablegen
 * 2. In `androidApp/build.gradle.kts` Plugin `com.google.gms.google-services` und
 *    `implementation(libs.firebase.messaging)` ergänzen
 * 3. Diese Datei durch eine Klasse ersetzen, die von `FirebaseMessagingService` erbt,
 *    `onNewToken` → Server `POST /devices`, `onMessageReceived` → System-Notification
 * 4. Service im Manifest registrieren
 *
 * Bis dahin liefert [app.chargeshare.platform.createPushTokenSource] kein Token;
 * der Server loggt Push-Ereignisse über [app.chargeshare.server.push.LoggingPushNotifier].
 */
object FcmMessagingServiceGuide {
    const val DOC = "README.de.md#push-benachrichtigungen-mvp"
}
