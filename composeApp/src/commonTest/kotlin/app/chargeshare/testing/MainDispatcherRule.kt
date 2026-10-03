package app.chargeshare.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * `viewModelScope` läuft auf `Dispatchers.Main`; in Tests wird sie durch einen sofort ausführenden Dispatcher ersetzt.
 * Aufruf: in `@BeforeTest` [install], in `@AfterTest` [uninstall].
 */
@OptIn(ExperimentalCoroutinesApi::class)
object TestMain {
    fun install() = Dispatchers.setMain(UnconfinedTestDispatcher())

    fun uninstall() = Dispatchers.resetMain()
}
