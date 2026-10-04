package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.presentation.wallbox.FavoritesViewModel
import app.chargeshare.testing.FakeFavoritesRepository
import app.chargeshare.testing.TestMain
import app.chargeshare.testing.failure
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FavoritesViewModelTest {
    @BeforeTest fun setUp() = TestMain.install()

    @AfterTest fun tearDown() = TestMain.uninstall()

    @Test
    fun loadsFavoritesOnStart() {
        val viewModel = FavoritesViewModel(FakeFavoritesRepository(setOf("a", "b")))
        assertEquals(setOf("a", "b"), viewModel.state.value.favoriteIds)
        assertTrue(viewModel.state.value.isFavorite("a"))
        assertFalse(viewModel.state.value.isFavorite("c"))
    }

    @Test
    fun failedLoadLeavesHeartsEmptyWithoutError() {
        val repository = FakeFavoritesRepository().apply { idsResult = failure(AppError.Network) }
        val state = FavoritesViewModel(repository).state.value
        assertTrue(state.favoriteIds.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun toggleAddsAndRemoves() {
        val repository = FakeFavoritesRepository()
        val viewModel = FavoritesViewModel(repository)

        viewModel.onToggle("a")
        assertEquals(setOf("a"), viewModel.state.value.favoriteIds)
        assertEquals(listOf("a"), repository.added)
        assertTrue(viewModel.state.value.pendingIds.isEmpty(), "nach dem Speichern nicht mehr unterwegs")

        viewModel.onToggle("a")
        assertTrue(viewModel.state.value.favoriteIds.isEmpty())
        assertEquals(listOf("a"), repository.removed)
    }

    @Test
    fun failedAddIsRolledBackAndReported() {
        val repository = FakeFavoritesRepository().apply { failNext = AppError.Network }
        val viewModel = FavoritesViewModel(repository)

        viewModel.onToggle("a")
        assertFalse(viewModel.state.value.isFavorite("a"))
        assertEquals(AppError.Network, viewModel.state.value.error)

        viewModel.onErrorShown()
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun failedRemoveKeepsTheFavorite() {
        val repository = FakeFavoritesRepository(setOf("a")).apply { failNext = AppError.Server }
        val viewModel = FavoritesViewModel(repository)

        viewModel.onToggle("a")
        assertTrue(viewModel.state.value.isFavorite("a"))
        assertEquals(AppError.Server, viewModel.state.value.error)
    }

    @Test
    fun nextToggleClearsOldError() {
        val repository = FakeFavoritesRepository().apply { failNext = AppError.Network }
        val viewModel = FavoritesViewModel(repository)
        viewModel.onToggle("a")
        viewModel.onToggle("a")
        assertNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.isFavorite("a"))
    }
}
