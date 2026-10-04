package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.data.DeepLink
import app.chargeshare.data.DeepLinkInbox
import app.chargeshare.data.Outcome
import app.chargeshare.presentation.host.PayoutViewModel
import app.chargeshare.shared.model.PayoutState
import app.chargeshare.shared.model.PayoutStatus
import app.chargeshare.testing.FakePayoutRepository
import app.chargeshare.testing.TestMain
import app.chargeshare.testing.failure
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PayoutViewModelTest {
    private val repository = FakePayoutRepository()

    @BeforeTest fun setUp() = TestMain.install()

    @AfterTest fun tearDown() = TestMain.uninstall()

    private fun status(state: PayoutState) = Outcome.Success(PayoutStatus(state))

    @Test
    fun initialStateIsHiddenUntilLoaded() {
        val viewModel = PayoutViewModel(repository)
        assertNull(viewModel.state.value.state)
        assertFalse(viewModel.state.value.isVisible)
    }

    @Test
    fun refreshShowsNotStartedWithoutAskingStripe() {
        val viewModel = PayoutViewModel(repository)
        viewModel.refresh()
        assertEquals(PayoutState.NOT_STARTED, viewModel.state.value.state)
        assertTrue(viewModel.state.value.isVisible)
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun pendingAccountIsSyncedWithStripeOnNextRefresh() {
        repository.statusResult = status(PayoutState.PENDING)
        val viewModel = PayoutViewModel(repository)
        viewModel.refresh() // erster Aufruf: gespeicherter Stand
        repository.statusResult = status(PayoutState.PENDING)
        repository.refreshResult = status(PayoutState.READY)
        viewModel.refresh() // jetzt ist „in Prüfung“ bekannt: Stripe fragen
        assertEquals(PayoutState.READY, viewModel.state.value.state)
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun notRequiredHidesTheCard() {
        repository.statusResult = status(PayoutState.NOT_REQUIRED)
        val viewModel = PayoutViewModel(repository)
        viewModel.refresh()
        assertFalse(viewModel.state.value.isVisible)
    }

    @Test
    fun readyStaysVisibleWithoutSetupAction() {
        repository.statusResult = status(PayoutState.READY)
        val viewModel = PayoutViewModel(repository)
        viewModel.refresh()
        assertTrue(viewModel.state.value.isVisible)
    }

    @Test
    fun loadFailureShowsErrorAndKeepsCardVisible() {
        repository.statusResult = failure(AppError.Network)
        val viewModel = PayoutViewModel(repository)
        viewModel.refresh()
        assertEquals(AppError.Network, viewModel.state.value.error)
        assertTrue(viewModel.state.value.isVisible)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun setUpFetchesLinkAndHandingItOverClearsIt() {
        val viewModel = PayoutViewModel(repository)
        viewModel.onSetUpClicked()
        assertEquals("https://connect.stripe.test/onboard", viewModel.state.value.onboardingUrl)
        assertFalse(viewModel.state.value.isStarting)

        viewModel.onOnboardingUrlHandled(opened = true)
        assertNull(viewModel.state.value.onboardingUrl)
        assertFalse(viewModel.state.value.openFailed)
    }

    @Test
    fun browserThatCannotOpenIsReported() {
        val viewModel = PayoutViewModel(repository)
        viewModel.onSetUpClicked()
        viewModel.onOnboardingUrlHandled(opened = false)
        assertTrue(viewModel.state.value.openFailed)
    }

    @Test
    fun setUpFailureIsShownWithoutLink() {
        repository.onboardingResult = failure(AppError.Server)
        val viewModel = PayoutViewModel(repository)
        viewModel.onSetUpClicked()
        assertEquals(AppError.Server, viewModel.state.value.error)
        assertNull(viewModel.state.value.onboardingUrl)
        assertFalse(viewModel.state.value.isStarting)
    }

    @Test
    fun returningFromOnboardingSyncsImmediatelyAndUnlocksHost() {
        repository.statusResult = status(PayoutState.PENDING)
        repository.refreshResult = status(PayoutState.READY)
        val viewModel = PayoutViewModel(repository)
        viewModel.onReturnedFromOnboarding()
        assertEquals(PayoutState.READY, viewModel.state.value.state)
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun expiredLinkRequestsANewOne() {
        val viewModel = PayoutViewModel(repository)
        viewModel.onOnboardingExpired()
        assertEquals(1, repository.onboardingCalls)
        assertEquals("https://connect.stripe.test/onboard", viewModel.state.value.onboardingUrl)
    }

    @Test
    fun deepLinksAreParsedAndConsumedOnce() {
        assertEquals(DeepLink.OnboardingDone, DeepLink.parse("chargeshare://onboarding-done"))
        assertEquals(DeepLink.OnboardingRefresh, DeepLink.parse("chargeshare://onboarding-refresh/"))
        assertNull(DeepLink.parse("https://evil.test/onboarding-done"))
        assertNull(DeepLink.parse(null))

        val inbox = DeepLinkInbox()
        assertFalse(inbox.offer("chargeshare://unknown"))
        assertNull(inbox.pending.value)
        assertTrue(inbox.offer("chargeshare://onboarding-done"))
        assertEquals(DeepLink.OnboardingDone, inbox.pending.value)
        inbox.offer("chargeshare://onboarding-refresh")
        inbox.consume(DeepLink.OnboardingDone) // veraltet: der neue Link bleibt
        assertEquals(DeepLink.OnboardingRefresh, inbox.pending.value)
        inbox.consume(DeepLink.OnboardingRefresh)
        assertNull(inbox.pending.value)
    }
}
