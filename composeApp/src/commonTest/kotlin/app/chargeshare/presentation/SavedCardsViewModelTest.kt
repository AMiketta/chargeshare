package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.data.Outcome
import app.chargeshare.payment.PaymentResult
import app.chargeshare.presentation.profile.AddCardState
import app.chargeshare.presentation.profile.SavedCardsViewModel
import app.chargeshare.shared.model.SavedCard
import app.chargeshare.shared.model.SavedCardsResponse
import app.chargeshare.testing.FakePaymentRepository
import app.chargeshare.testing.TestMain
import app.chargeshare.testing.failure
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Zahlungsmethoden im Profil: Liste, Entfernen, Karte hinzufügen. */
class SavedCardsViewModelTest {
    private val payments = FakePaymentRepository()
    private val visa = SavedCard("pm_1", "visa", "4242", 12, 2030)
    private val mastercard = SavedCard("pm_2", "mastercard", "4444", 1, 2031)

    @BeforeTest
    fun setUp() {
        TestMain.install()
        payments.savedCardsResult = Outcome.Success(SavedCardsResponse(supported = true, cards = listOf(visa, mastercard)))
    }

    @AfterTest fun tearDown() = TestMain.uninstall()

    private fun viewModel(canAddCard: Boolean = true) = SavedCardsViewModel(payments, canAddCard).also { it.refresh() }

    @Test
    fun refreshLoadsTheCards() {
        val state = viewModel().state.value
        assertEquals(listOf(visa, mastercard), state.cards)
        assertTrue(state.supported)
        assertFalse(state.isLoading)
    }

    @Test
    fun serverWithoutStripeIsReportedAsUnsupported() {
        payments.savedCardsResult = Outcome.Success(SavedCardsResponse(supported = false))
        val viewModel = viewModel()
        assertFalse(viewModel.state.value.supported)
        viewModel.onAddCard()
        assertEquals(0, payments.setupIntentCalls)
    }

    @Test
    fun loadErrorIsExposed() {
        payments.savedCardsResult = failure(AppError.Network)
        assertEquals(AppError.Network, viewModel().state.value.error)
    }

    @Test
    fun removingACardNeedsConfirmationAndUpdatesTheList() {
        val viewModel = viewModel()
        viewModel.onRemoveRequested(visa)
        assertEquals(visa, viewModel.state.value.removeCandidate)
        assertTrue(payments.removedCards.isEmpty())

        viewModel.onRemoveConfirmed()
        assertEquals(listOf("pm_1"), payments.removedCards)
        assertEquals(listOf(mastercard), viewModel.state.value.cards)
        assertEquals(null, viewModel.state.value.removeCandidate)
    }

    @Test
    fun dismissingTheConfirmationKeepsTheCard() {
        val viewModel = viewModel()
        viewModel.onRemoveRequested(visa)
        viewModel.onRemoveDismissed()
        viewModel.onRemoveConfirmed()
        assertTrue(payments.removedCards.isEmpty())
        assertEquals(2, viewModel.state.value.cards.size)
    }

    @Test
    fun failedRemovalKeepsTheCardAndShowsTheError() {
        payments.removeResult = failure(AppError.Server)
        val viewModel = viewModel()
        viewModel.onRemoveRequested(visa)
        viewModel.onRemoveConfirmed()
        assertEquals(AppError.Server, viewModel.state.value.actionError)
        assertEquals(2, viewModel.state.value.cards.size)
    }

    @Test
    fun addingACardPreparesTheSheetAndRefreshesAfterSuccess() {
        val viewModel = viewModel()
        viewModel.onAddCard()
        val ready = assertIs<AddCardState.Ready>(viewModel.state.value.addCard)
        assertEquals("seti_1_secret_x", ready.request.clientSecret)

        viewModel.onSheetOpened()
        assertEquals(AddCardState.SheetOpen, viewModel.state.value.addCard)

        payments.savedCardsResult = Outcome.Success(SavedCardsResponse(true, listOf(visa, mastercard, SavedCard("pm_3", "amex", "0005", 5, 2032))))
        viewModel.onSaveCardResult(PaymentResult.Completed)
        assertEquals(AddCardState.Idle, viewModel.state.value.addCard)
        assertEquals(3, viewModel.state.value.cards.size)
    }

    @Test
    fun resultsBeforeTheSheetIsOpenAreIgnored() {
        val viewModel = viewModel()
        viewModel.onAddCard()
        viewModel.onSaveCardResult(PaymentResult.Completed)
        assertIs<AddCardState.Ready>(viewModel.state.value.addCard)
    }

    @Test
    fun canceledSheetEndsQuietlyAndFailureShowsAHint() {
        val viewModel = viewModel()
        viewModel.onAddCard()
        viewModel.onSheetOpened()
        viewModel.onSaveCardResult(PaymentResult.Canceled)
        assertEquals(AddCardState.Idle, viewModel.state.value.addCard)
        assertFalse(viewModel.state.value.addCardFailed)

        viewModel.onAddCard()
        viewModel.onSheetOpened()
        viewModel.onSaveCardResult(PaymentResult.Failed("declined"))
        assertTrue(viewModel.state.value.addCardFailed)
    }

    @Test
    fun setupIntentErrorIsShownAndNoSheetOpens() {
        payments.setupIntentResult = failure(AppError.Server)
        val viewModel = viewModel()
        viewModel.onAddCard()
        assertEquals(AddCardState.Idle, viewModel.state.value.addCard)
        assertEquals(AppError.Server, viewModel.state.value.actionError)
    }

    @Test
    fun platformWithoutCardSetupCannotAddCards() {
        val viewModel = viewModel(canAddCard = false)
        viewModel.onAddCard()
        assertEquals(0, payments.setupIntentCalls)
    }
}
