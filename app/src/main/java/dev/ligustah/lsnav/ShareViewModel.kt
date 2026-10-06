package dev.ligustah.lsnav

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ShareState(
    val configuration: AppConfiguration = AppConfiguration(),
    val places: List<PlaceResult> = emptyList(),
    val selected: Int = 0,
    val loading: Boolean = false,
    val sending: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null
)

class ShareViewModel @JvmOverloads constructor(
    application: Application,
    private val savedState: SavedStateHandle,
    private val gateway: NavigationGateway = NavigationRepository(),
    private val resolver: CoordinateResolver = CoordinateResolver(application),
    private val configurations: kotlinx.coroutines.flow.Flow<AppConfiguration> = AppSettings(application).configuration
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(ShareState(sent = savedState["sent"] ?: false))
    val state: StateFlow<ShareState> = mutableState
    private var input: String? = null

    fun start(text: String) {
        if (input != null || mutableState.value.sent) return
        input = text
        retry()
    }
    fun select(index: Int) {
        if (!mutableState.value.sending && index in mutableState.value.places.indices) {
            savedState["selected"] = index
            mutableState.value = mutableState.value.copy(selected = index)
        }
    }
    fun retry() {
        if (mutableState.value.loading || mutableState.value.sending || mutableState.value.sent) return
        val text = input ?: return
        mutableState.value = mutableState.value.copy(loading = true, places = emptyList(), error = null)
        viewModelScope.launch {
            try {
                val configuration = configurations.first()
                require(configuration.isReady) { "Select a scooter and enter your token in the main app's Settings first." }
                val places = resolver.resolve(text)
                require(places.isNotEmpty()) { "No destination found. Share an address or a link with an explicit destination." }
                val selected = (savedState.get<Int>("selected") ?: 0).coerceIn(places.indices)
                mutableState.value = mutableState.value.copy(configuration = configuration, places = places, selected = selected)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { mutableState.value = mutableState.value.copy(error = userMessage(error))
            } finally { mutableState.value = mutableState.value.copy(loading = false) }
        }
    }
    fun confirm() {
        val state = mutableState.value
        if (state.sending || state.sent || state.loading) return
        val place = state.places.getOrNull(state.selected) ?: return
        mutableState.value = state.copy(sending = true, error = null)
        viewModelScope.launch {
            try {
                val current = configurations.first()
                require(current == state.configuration) { "Settings have changed. Retry to confirm the scooter again." }
                gateway.setDestination(state.configuration, place)
                savedState["sent"] = true
                mutableState.value = mutableState.value.copy(sent = true)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { mutableState.value = mutableState.value.copy(error = userMessage(error))
            } finally { mutableState.value = mutableState.value.copy(sending = false) }
        }
    }
}
