package dev.ligustah.lsnav

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

 data class ShareState(
    val configuration: AppConfiguration = AppConfiguration(),
    val scooters: RemoteData<List<ScooterSummary>> = RemoteData(),
    val places: RemoteData<List<PlaceResult>> = RemoteData(),
    val selected: Int = 0,
    val sending: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null
)

class ShareViewModel @JvmOverloads constructor(
    application: Application,
    private val savedState: SavedStateHandle,
    private val gateway: NavigationGateway = NavigationRepository.get(application),
    private val resolver: CoordinateResolver = CoordinateResolver(application),
    private val configurations: kotlinx.coroutines.flow.Flow<AppConfiguration> = AppSettings(application).configuration
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(ShareState(sent = savedState["sent"] ?: false, selected = savedState["selected"] ?: 0))
    val state: StateFlow<ShareState> = mutableState
    private var input: String? = null
    private var storedConfiguration: AppConfiguration? = null
    private var loadJob: Job? = null
    private var generation = 0

    fun start(text: String) {
        if (input != null || mutableState.value.sent) return
        input = text
        retry()
    }

    fun select(index: Int) {
        if (!mutableState.value.sending && index in mutableState.value.places.value.orEmpty().indices) {
            savedState["selected"] = index
            mutableState.value = mutableState.value.copy(selected = index)
        }
    }

    fun selectScooter(scooter: ScooterSummary) {
        val state = mutableState.value
        if (state.sending || state.sent || scooter !in state.scooters.value.orEmpty()) return
        mutableState.value = state.copy(configuration = state.configuration.copy(scooterId = scooter.id, scooterName = scooter.name))
    }

    fun chooseOnDemand() {
        if (!mutableState.value.sending && !mutableState.value.sent) {
            mutableState.value = mutableState.value.copy(configuration = mutableState.value.configuration.copy(scooterId = null, scooterName = ""))
        }
    }

    fun retry() {
        if (mutableState.value.sending || mutableState.value.sent) return
        val text = input ?: return
        val current = ++generation
        loadJob?.cancel()
        mutableState.value = mutableState.value.copy(places = mutableState.value.places.refreshing(), scooters = mutableState.value.scooters.refreshing(), error = null)
        loadJob = viewModelScope.launch {
            try {
                val configuration = configurations.first()
                require(configuration.isAuthenticated) { "Sign in or enter an API token in the main app's Settings first." }
                val scooters = gateway.scooters(configuration).distinctBy { it.id }
                if (current != generation) return@launch
                val selectedId = if (storedConfiguration?.accountKey == configuration.accountKey) mutableState.value.configuration.scooterId else configuration.scooterId
                val scooter = scooters.firstOrNull { it.id == selectedId }
                storedConfiguration = configuration
                val selectedConfiguration = configuration.copy(scooterId = scooter?.id, scooterName = scooter?.name.orEmpty())
                mutableState.value = mutableState.value.copy(scooters = RemoteData.loaded(scooters), configuration = selectedConfiguration)
                val places = resolver.resolve(text)
                require(places.isNotEmpty()) { "No destination found. Share an address or a link with an explicit destination." }
                if (current == generation) {
                    val selected = (savedState.get<Int>("selected") ?: 0).coerceIn(places.indices)
                    mutableState.value = mutableState.value.copy(places = RemoteData.loaded(places), selected = selected)
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (current == generation) mutableState.value = mutableState.value.copy(error = userMessage(error),
                    places = mutableState.value.places.failed(userMessage(error)))
            } finally {
                if (current == generation) mutableState.value = mutableState.value.copy(
                    places = mutableState.value.places.copy(loading = false), scooters = mutableState.value.scooters.copy(loading = false))
            }
        }
    }

    fun confirm() {
        val state = mutableState.value
        if (state.sending || state.sent || state.places.loading || !state.configuration.isReady) return
        val place = state.places.value?.getOrNull(state.selected) ?: return
        if (!place.coordinates.isValid()) return
        val config = state.configuration
        mutableState.value = state.copy(sending = true, error = null)
        viewModelScope.launch {
            try {
                val current = configurations.first()
                require(current == storedConfiguration && config.accountKey == current.accountKey) {
                    "Settings have changed. Retry to confirm the scooter again."
                }
                require(config.scooterId == null || state.scooters.value.orEmpty().any { it.id == config.scooterId }) {
                    "Choose an available scooter again before sending."
                }
                gateway.setDestination(config, place)
                savedState["sent"] = true
                mutableState.value = mutableState.value.copy(sent = true)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { mutableState.value = mutableState.value.copy(error = userMessage(error))
            } finally { mutableState.value = mutableState.value.copy(sending = false) }
        }
    }
}
