package app.chargeshare.android

import android.app.Application
import app.chargeshare.di.AppContainer
import app.chargeshare.platform.AndroidReceiptSharer
import app.chargeshare.platform.AndroidUrlOpener
import app.chargeshare.platform.PlatformContext
import app.chargeshare.platform.createPreferenceStorage
import app.chargeshare.platform.createPushTokenSource
import app.chargeshare.platform.createSecureTokenStore

/** Hält den [AppContainer] prozessweit, damit er Konfigurationsänderungen (Drehen) der Activity überlebt. */
class ChargeShareApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            apiBaseUrl = BuildConfig.API_BASE_URL,
            tokenStore = createSecureTokenStore(PlatformContext(this)),
            preferenceStorage = createPreferenceStorage(PlatformContext(this)),
            showDebugInfo = BuildConfig.DEBUG,
            urlOpener = AndroidUrlOpener(this),
            receiptSharer = AndroidReceiptSharer(this),
            pushTokenSource = createPushTokenSource(PlatformContext(this)),
        )
    }
}
