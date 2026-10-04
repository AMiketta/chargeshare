package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.platform.PickedPhoto
import app.chargeshare.presentation.host.EditWallboxViewModel
import app.chargeshare.presentation.host.PhotoIssue
import app.chargeshare.testing.FakeHostRepository
import app.chargeshare.testing.TestMain
import app.chargeshare.testing.failure
import app.chargeshare.testing.testWallbox
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WallboxPhotosViewModelTest {
    private val repository = FakeHostRepository()
    private val firstPhoto = "/api/v1/wallboxes/wb-1/photos/a.jpg"
    private val wallbox = testWallbox("wb-1").copy(photos = listOf(firstPhoto))
    private val photo = PickedPhoto(byteArrayOf(1, 2, 3), "foto.jpg", "image/jpeg")

    @BeforeTest fun setUp() = TestMain.install()

    @AfterTest fun tearDown() = TestMain.uninstall()

    private fun viewModel() = EditWallboxViewModel(wallbox, repository, TimeZone.UTC)

    @Test
    fun formStartsWithExistingPhotos() {
        assertEquals(listOf(firstPhoto), viewModel().state.value.photos)
    }

    @Test
    fun uploadedPhotoReplacesTheListWithTheServerAnswer() {
        val viewModel = viewModel()
        viewModel.onPhotoPicked(photo)
        assertEquals(Triple("wb-1", "foto.jpg", "image/jpeg"), repository.uploadedPhotos.single())
        val state = viewModel.state.value
        assertEquals(listOf("/api/v1/wallboxes/wb-1/photos/new.jpg"), state.photos)
        assertFalse(state.photoBusy)
        assertNull(state.photoError)
    }

    @Test
    fun removedPhotoIsSentToTheServerAndDropped() {
        val viewModel = viewModel()
        viewModel.onPhotoRemoved(firstPhoto)
        assertEquals("wb-1" to firstPhoto, repository.removedPhotos.single())
        assertTrue(viewModel.state.value.photos.isEmpty())
    }

    @Test
    fun failedUploadKeepsPhotosAndShowsTheError() {
        repository.photoResult = failure(AppError.Conflict)
        val viewModel = viewModel()
        viewModel.onPhotoPicked(photo)
        val state = viewModel.state.value
        assertEquals(listOf(firstPhoto), state.photos)
        assertEquals(PhotoIssue.Failed(AppError.Conflict), state.photoError)
        assertFalse(state.photoBusy)
    }

    @Test
    fun unreadableFileShowsAnErrorWithoutUpload() {
        val viewModel = viewModel()
        viewModel.onPhotoUnreadable()
        assertEquals(PhotoIssue.Unreadable, viewModel.state.value.photoError)
        assertTrue(repository.uploadedPhotos.isEmpty())
    }

    @Test
    fun nextUploadClearsEarlierError() {
        repository.photoResult = failure(AppError.Network)
        val viewModel = viewModel()
        viewModel.onPhotoPicked(photo)
        assertEquals(PhotoIssue.Failed(AppError.Network), viewModel.state.value.photoError)
        repository.photoResult = null
        viewModel.onPhotoPicked(photo)
        assertNull(viewModel.state.value.photoError)
        viewModel.onPhotoErrorShown()
        assertNull(viewModel.state.value.photoError)
    }

    @Test
    fun savingTheFormDoesNotSendPhotos() {
        val viewModel = viewModel()
        viewModel.onChange { it.copy(description = "Neu") }
        viewModel.onSubmit()
        assertNull(repository.updatedRequests.single().second.photos)
    }
}
