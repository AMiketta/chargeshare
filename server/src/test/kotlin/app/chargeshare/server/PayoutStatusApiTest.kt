package app.chargeshare.server

import app.chargeshare.shared.model.PayoutState
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Auszahlungsstatus des Hosts, sofortige Synchronisation mit Stripe und die Rückkehr-Seiten des Onboardings. */
class PayoutStatusApiTest {

    @Test
    fun fakeProviderDoesNotRequireAPayoutAccount() = serverTest {
        val host = registerUser("host@example.com", host = true)

        assertEquals(PayoutState.NOT_REQUIRED, host.api.payoutStatus().state)
        assertEquals(PayoutState.NOT_REQUIRED, host.api.refreshPayoutStatus().state)
    }

    @Test
    fun statusFollowsOnboardingFromNotStartedToPendingToReady() = serverTest(payments = PayoutGatedProvider()) {
        val provider = payments as PayoutGatedProvider
        val host = registerUser("host@example.com", host = true)
        assertEquals(PayoutState.NOT_STARTED, host.api.payoutStatus().state)
        assertTrue(host.api.payoutStatus().needsSetup)

        host.api.startPayoutOnboarding()
        assertEquals(PayoutState.PENDING, host.api.payoutStatus().state)
        assertEquals(PayoutState.PENDING, host.api.refreshPayoutStatus().state)

        provider.readyAccounts += host.api.me().stripeAccountId!!
        // Der Lesezugriff fragt Stripe nicht; erst die Synchronisation schaltet sofort frei.
        assertEquals(PayoutState.PENDING, host.api.payoutStatus().state)
        val refreshed = host.api.refreshPayoutStatus()
        assertEquals(PayoutState.READY, refreshed.state)
        assertFalse(refreshed.needsSetup)
        assertEquals(PayoutState.READY, host.api.payoutStatus().state)
    }

    @Test
    fun refreshKeepsPendingWhenStripeIsUnreachable() = serverTest(payments = PayoutGatedProvider()) {
        val provider = payments as PayoutGatedProvider
        val host = registerUser("host@example.com", host = true)
        host.api.startPayoutOnboarding()
        provider.readyAccounts += host.api.me().stripeAccountId!!
        provider.checkFailure = IllegalStateException("stripe down")

        assertEquals(PayoutState.PENDING, host.api.refreshPayoutStatus().state)

        provider.checkFailure = null
        assertEquals(PayoutState.READY, host.api.refreshPayoutStatus().state)
    }

    @Test
    fun statusIsPerUser() = serverTest(payments = PayoutGatedProvider()) {
        val provider = payments as PayoutGatedProvider
        val readyHost = registerUser("ready@example.com", host = true)
        val otherHost = registerUser("other@example.com", host = true)
        readyHost.api.startPayoutOnboarding()
        provider.readyAccounts += readyHost.api.me().stripeAccountId!!
        readyHost.api.refreshPayoutStatus()

        assertEquals(PayoutState.READY, readyHost.api.payoutStatus().state)
        assertEquals(PayoutState.NOT_STARTED, otherHost.api.payoutStatus().state)
    }

    @Test
    fun payoutStatusRequiresLogin() = serverTest {
        assertEquals(HttpStatusCode.Unauthorized, raw.get("/api/v1/payments/payout-status").status)
    }

    @Test
    fun onboardingReturnUrlsDefaultToTheAddressOfTheRequest() = serverTest(payments = PayoutGatedProvider()) {
        val provider = payments as PayoutGatedProvider
        registerUser("host@example.com", host = true).api.startPayoutOnboarding()

        val urls = provider.lastUrls!!
        assertEquals("http://localhost/onboarding/return", urls.returnUrl)
        assertEquals("http://localhost/onboarding/refresh", urls.refreshUrl)
    }

    @Test
    fun publicBaseUrlAndExplicitUrlsAreConfigurable() {
        var urlsFromBase: Pair<String, String>? = null
        serverTest(config = testConfig.copy(publicBaseUrl = "https://api.chargeshare.app/"), payments = PayoutGatedProvider()) {
            registerUser("host@example.com", host = true).api.startPayoutOnboarding()
            (payments as PayoutGatedProvider).lastUrls!!.let { urlsFromBase = it.returnUrl to it.refreshUrl }
        }
        assertEquals("https://api.chargeshare.app/onboarding/return" to "https://api.chargeshare.app/onboarding/refresh", urlsFromBase)

        val explicit = testConfig.copy(
            publicBaseUrl = "https://api.chargeshare.app",
            onboardingReturnUrl = "https://x.test/done",
            onboardingRefreshUrl = "https://x.test/again",
        )
        serverTest(config = explicit, payments = PayoutGatedProvider()) {
            registerUser("host@example.com", host = true).api.startPayoutOnboarding()
            val urls = (payments as PayoutGatedProvider).lastUrls!!
            assertEquals("https://x.test/done", urls.returnUrl)
            assertEquals("https://x.test/again", urls.refreshUrl)
        }
    }

    @Test
    fun returnPagesAreOpenAndRedirectIntoTheApp() = serverTest {
        val done = raw.get("/onboarding/return")
        assertEquals(HttpStatusCode.OK, done.status)
        val doneBody = done.bodyAsText()
        assertTrue("chargeshare://onboarding-done" in doneBody)
        assertTrue("http-equiv=\"refresh\"" in doneBody)

        val refresh = raw.get("/onboarding/refresh").bodyAsText()
        assertTrue("chargeshare://onboarding-refresh" in refresh)
    }
}
