package app.chargeshare.server

import app.chargeshare.server.payments.buildOnboardingLinkParams
import app.chargeshare.server.payments.buildRecipientAccountParams
import kotlin.test.Test
import kotlin.test.assertEquals

/** Connect-Konto und Onboarding-Link laufen ueber Accounts v2 (v1 ist fuer neue Plattformen gesperrt). */
class ConnectAccountParamsTest {

    @Test
    fun recipientAccountIsExpressWithTransfersCapability() {
        val params = buildRecipientAccountParams("host@example.com").toMap()

        assertEquals("host@example.com", params["contact_email"])
        assertEquals("express", params["dashboard"])
        assertEquals(mapOf("country" to "de"), params["identity"])
        assertEquals(
            mapOf("recipient" to mapOf("capabilities" to mapOf("stripe_balance" to mapOf("stripe_transfers" to mapOf("requested" to true))))),
            params["configuration"],
        )
        assertEquals(
            mapOf("responsibilities" to mapOf("fees_collector" to "application", "losses_collector" to "application")),
            params["defaults"],
        )
    }

    @Test
    fun onboardingLinkUsesAccountOnboardingUseCase() {
        val params = buildOnboardingLinkParams("acct_123", "https://x.test/refresh", "https://x.test/return").toMap()

        assertEquals("acct_123", params["account"])
        assertEquals(
            mapOf(
                "type" to "account_onboarding",
                "account_onboarding" to mapOf("refresh_url" to "https://x.test/refresh", "return_url" to "https://x.test/return"),
            ),
            params["use_case"],
        )
    }
}
