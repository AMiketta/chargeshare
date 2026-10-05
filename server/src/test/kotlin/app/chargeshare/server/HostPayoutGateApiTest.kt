package app.chargeshare.server

import app.chargeshare.server.db.Db
import app.chargeshare.server.db.DevSeed
import app.chargeshare.server.payments.OnboardingInfo
import app.chargeshare.server.payments.OnboardingUrls
import app.chargeshare.server.payments.PaymentProvider
import app.chargeshare.server.payments.canReceiveTransfers
import app.chargeshare.server.services.PayoutService
import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.shared.model.CreateBookingRequest
import app.chargeshare.shared.model.ErrorCodes
import app.chargeshare.shared.model.WallboxSearch
import com.stripe.model.v2.core.Account
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Wie Stripe: Hosts brauchen ein Konto, dessen Onboarding abgeschlossen ist ([readyAccounts]). */
class PayoutGatedProvider(val base: ScriptedPaymentProvider = ScriptedPaymentProvider()) : PaymentProvider by base {
    override val requiresPayoutAccount = true
    val readyAccounts: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    /** Wenn gesetzt, wirft [isPayoutReady] diesen Fehler (Stripe nicht erreichbar). */
    @Volatile var checkFailure: Throwable? = null
    val checks = AtomicInteger()
    private val accounts = AtomicInteger()

    override fun isPayoutReady(accountId: String): Boolean {
        checks.incrementAndGet()
        checkFailure?.let { throw it }
        return accountId in readyAccounts
    }

    /** Zuletzt an [createOnboardingLink] übergebene Rückkehr-Adressen. */
    @Volatile var lastUrls: OnboardingUrls? = null

    override fun createOnboardingLink(email: String, existingAccountId: String?, urls: OnboardingUrls): OnboardingInfo {
        lastUrls = urls
        return OnboardingInfo(existingAccountId ?: "acct_test_${accounts.incrementAndGet()}", "https://stripe.test/onboard")
    }
}

/** Wallboxes von Hosts ohne abgeschlossenes Stripe-Onboarding sind nicht buchbar (409 statt Fehler bei Stripe). */
class HostPayoutGateApiTest {

    private fun payoutsReadyInDb(email: String): Boolean = transaction(TestDb.database) {
        var ready = false
        exec("SELECT payouts_ready FROM users WHERE email = '$email'") { if (it.next()) ready = it.getBoolean(1) }
        ready
    }

    private fun setPayoutsReady(email: String, ready: Boolean) {
        transaction(TestDb.database) { exec("UPDATE users SET payouts_ready = $ready WHERE email = '$email'") }
    }

    private suspend fun ServerScope.hostWithWallbox(email: String = "host@example.com"): Pair<TestClient, String> {
        val host = registerUser(email, host = true)
        val wallbox = host.api.createWallbox(newWallbox()).also { host.api.setWallboxOnline(it.id, true) }
        return host to wallbox.id
    }

    private fun tomorrow(wallboxId: String, day: Long = 1) =
        CreateBookingRequest(wallboxId, inFuture(day, 10), inFuture(day, 12))

    private suspend fun assertPending(block: suspend () -> Unit) {
        val e = assertFailsWith<ApiException> { block() }
        assertEquals(409, e.status)
        assertEquals(ErrorCodes.HOST_PAYOUTS_PENDING, e.code)
        assertTrue("Auszahlungen" in e.message.orEmpty(), "deutsche Meldung erwartet: ${e.message}")
    }

    @Test
    fun hostWithoutAccountCannotBeBookedAndWallboxShowsIt() = serverTest(payments = PayoutGatedProvider()) {
        val (host, wallboxId) = hostWithWallbox()
        val driver = registerUser("fahrer@example.com")

        assertPending { driver.api.createBooking(tomorrow(wallboxId)) }

        assertFalse(driver.api.wallbox(wallboxId).payoutsReady)
        assertFalse(driver.api.wallbox(wallboxId).isBookable)
        assertFalse(driver.api.searchWallboxes(WallboxSearch()).wallboxes.single().payoutsReady)
        assertEquals(emptyList(), host.api.hostBookings().bookings)
    }

    @Test
    fun hostWithUnfinishedOnboardingStaysBlockedUntilStripeConfirms() = serverTest(payments = PayoutGatedProvider()) {
        val provider = payments as PayoutGatedProvider
        val (host, wallboxId) = hostWithWallbox()
        val driver = registerUser("fahrer@example.com")
        host.api.startPayoutOnboarding()
        val account = host.api.me().stripeAccountId!!

        assertPending { driver.api.createBooking(tomorrow(wallboxId)) }
        assertTrue(provider.checks.get() > 0, "Stripe wurde nach dem Kontostatus gefragt")
        assertFalse(payoutsReadyInDb("host@example.com"))

        provider.readyAccounts += account
        val booking = driver.api.createBooking(tomorrow(wallboxId)).booking!!
        assertEquals(BookingStatus.PENDING, booking.status)
        assertTrue(payoutsReadyInDb("host@example.com"), "Status wird gespeichert")
        assertTrue(driver.api.wallbox(wallboxId).payoutsReady)

        // Ab jetzt ohne weitere Stripe-Abfrage
        val checks = provider.checks.get()
        driver.api.createBooking(tomorrow(wallboxId, day = 2))
        assertEquals(checks, provider.checks.get())
    }

    @Test
    fun stripeOutageIsAConflictNotAServerError() = serverTest(payments = PayoutGatedProvider()) {
        val provider = payments as PayoutGatedProvider
        val (host, wallboxId) = hostWithWallbox()
        val driver = registerUser("fahrer@example.com")
        host.api.startPayoutOnboarding()
        provider.checkFailure = IllegalStateException("stripe down")

        assertPending { driver.api.createBooking(tomorrow(wallboxId)) }
    }

    @Test
    fun confirmedHostIsBookable() = serverTest(payments = PayoutGatedProvider()) {
        val (_, wallboxId) = hostWithWallbox()
        val driver = registerUser("fahrer@example.com")
        setPayoutsReady("host@example.com", true)

        assertTrue(driver.api.wallbox(wallboxId).isBookable)
        assertEquals(BookingStatus.PENDING, driver.api.createBooking(tomorrow(wallboxId)).booking!!.status)
    }

    @Test
    fun bookingUnknownWallboxIsStillNotFound() = serverTest(payments = PayoutGatedProvider()) {
        val driver = registerUser("fahrer@example.com")
        val e = assertFailsWith<ApiException> {
            driver.api.createBooking(tomorrow("00000000-0000-0000-0000-000000000000"))
        }
        assertEquals(404, e.status)
    }

    @Test
    fun onboardingReturnReleasesWallboxes() = serverTest(payments = PayoutGatedProvider()) {
        val provider = payments as PayoutGatedProvider
        val (host, wallboxId) = hostWithWallbox()
        host.api.startPayoutOnboarding()
        provider.readyAccounts += host.api.me().stripeAccountId!!

        raw.getAuth("/api/v1/payments/onboard/return", host.store.accessToken())
        assertTrue(registerUser("fahrer@example.com").api.wallbox(wallboxId).payoutsReady)
    }

    @Test
    fun backgroundSweepReleasesFinishedHostsOnly() = serverTest(payments = PayoutGatedProvider()) {
        val provider = payments as PayoutGatedProvider
        val (done, doneBox) = hostWithWallbox("fertig@example.com")
        val (open, openBox) = hostWithWallbox("offen@example.com")
        done.api.startPayoutOnboarding()
        open.api.startPayoutOnboarding()
        provider.readyAccounts += done.api.me().stripeAccountId!!

        val released = PayoutService(Db(TestDb.database), provider).refreshPending()

        assertEquals(1, released)
        val reader = registerUser("fahrer@example.com")
        assertTrue(reader.api.wallbox(doneBox).payoutsReady)
        assertFalse(reader.api.wallbox(openBox).payoutsReady)
    }

    @Test
    fun payingPendingBookingIsBlockedWhenPayoutsAreNoLongerReady() {
        val scripted = ScriptedPaymentProvider()
        serverTest(payments = PayoutGatedProvider(scripted)) {
            val (_, wallboxId) = hostWithWallbox()
            val driver = registerUser("fahrer@example.com")
            setPayoutsReady("host@example.com", true)
            val booking = driver.api.createBooking(tomorrow(wallboxId)).booking!!

            // Stripe-Intent ist abgelaufen und das Konto nicht mehr bestaetigt: kein neuer Intent fuer eine nicht auszahlbare Wallbox
            scripted.canceledIntents += booking.stripePaymentIntentId!!
            setPayoutsReady("host@example.com", false)

            assertPending { driver.api.payBooking(booking.id) }
            assertEquals(1, scripted.amounts.size, "es wurde kein zweiter PaymentIntent angelegt")
        }
    }

    @Test
    fun fakeProviderKeepsEverythingBookable() = serverTest {
        val (_, wallboxId) = hostWithWallbox()
        val driver = registerUser("fahrer@example.com")

        assertTrue(driver.api.wallbox(wallboxId).payoutsReady)
        assertEquals(BookingStatus.CONFIRMED, driver.api.createBooking(tomorrow(wallboxId)).booking!!.status)
    }

    @Test
    fun devSeedHostStaysBookableWithStripe() = serverTest(payments = PayoutGatedProvider()) {
        DevSeed.run(Db(TestDb.database), 4)
        val boxes = registerUser("fahrer@example.com").api.searchWallboxes(WallboxSearch(limit = 100)).wallboxes
        assertTrue(boxes.isNotEmpty())
        assertTrue(boxes.all { it.payoutsReady })
    }

    @Test
    fun devSeedRepairsHostMigratedWithoutPayoutsReady() = serverTest(payments = PayoutGatedProvider()) {
        DevSeed.run(Db(TestDb.database), 4)
        setPayoutsReady(DevSeed.HOST_EMAIL, false) // Zustand einer Datenbank, die vor Migration V4 angelegt wurde
        DevSeed.run(Db(TestDb.database), 4)
        assertTrue(payoutsReadyInDb(DevSeed.HOST_EMAIL))
    }

    @Test
    fun devSeedClearsLeftoverStripeAccountSoDemoHostStaysBookableWithoutTransfer() = serverTest(
        payments = PayoutGatedProvider(ScriptedPaymentProvider()),
    ) {
        DevSeed.run(Db(TestDb.database), 4)
        // Demo-Host hat lokal Stripe-Onboarding gestartet: Konto ohne Transfers-Capability in der DB.
        transaction(TestDb.database) {
            exec("UPDATE users SET stripe_account_id = 'acct_demo_incomplete', payouts_ready = true WHERE email = '${DevSeed.HOST_EMAIL}'")
        }
        DevSeed.run(Db(TestDb.database), 4)

        var account: String? = "still-set"
        transaction(TestDb.database) {
            exec("SELECT stripe_account_id FROM users WHERE email = '${DevSeed.HOST_EMAIL}'") {
                account = if (it.next()) it.getString(1) else "missing"
            }
        }
        assertTrue(account == null, "Demo-Host darf kein Connect-Konto behalten, war: $account")
        assertTrue(payoutsReadyInDb(DevSeed.HOST_EMAIL))

        val driver = registerUser("fahrer@example.com")
        val box = driver.api.searchWallboxes(WallboxSearch(limit = 100)).wallboxes.first { it.name.contains("Marktplatz") }
        val booking = driver.api.createBooking(tomorrow(box.id)).booking!!
        assertEquals(BookingStatus.PENDING, booking.status)
        val scripted = (payments as PayoutGatedProvider).base
        assertEquals(listOf<String?>(null), scripted.destinations, "Buchung ohne Destination Charge (kein Transfer an Demo-Host)")
    }

    @Test
    fun accountIsReadyOnlyWithActiveTransfers() {
        assertFalse(Account().canReceiveTransfers())
        fun account(status: String?): Account {
            val transfers = Account.Configuration.Recipient.Capabilities.StripeBalance.StripeTransfers().apply { this.status = status }
            val balance = Account.Configuration.Recipient.Capabilities.StripeBalance().apply { stripeTransfers = transfers }
            val capabilities = Account.Configuration.Recipient.Capabilities().apply { stripeBalance = balance }
            val recipient = Account.Configuration.Recipient().apply { this.capabilities = capabilities }
            return Account().apply { configuration = Account.Configuration().apply { this.recipient = recipient } }
        }
        assertTrue(account("active").canReceiveTransfers())
        assertFalse(account("restricted").canReceiveTransfers())
        assertFalse(account("pending").canReceiveTransfers())
    }
}
