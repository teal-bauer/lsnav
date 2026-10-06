package dev.ligustah.lsnav

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.ligustah.lsnav.api.generated.models.Destination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class NavigationState(
    val configuration: AppConfiguration = AppConfiguration(),
    val loading: Boolean = false,
    val sending: Boolean = false,
    val destination: Destination? = null,
    val query: String = "",
    val results: List<PlaceResult> = emptyList(),
    val searching: Boolean = false,
    val error: String? = null
)

class NavigationViewModel @JvmOverloads constructor(
    application: Application,
    private val savedState: SavedStateHandle,
    private val gateway: NavigationGateway = NavigationRepository(),
    private val resolver: CoordinateResolver = CoordinateResolver(application),
    private val configurations: kotlinx.coroutines.flow.Flow<AppConfiguration> = AppSettings(application).configuration
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(NavigationState(query = savedState["query"] ?: ""))
    val state: StateFlow<NavigationState> = mutableState
    private var refreshJob: Job? = null
    private var commandJob: Job? = null
    private var searchJob: Job? = null
    private var refreshGeneration = 0
    private var searchGeneration = 0

    init {
        viewModelScope.launch {
            try {
                configurations.collect { config ->
                    if (config != mutableState.value.configuration) {
                        refreshJob?.cancel()
                        commandJob?.cancel()
                        mutableState.value = mutableState.value.copy(configuration = config, destination = null, sending = false, error = null)
                        refresh()
                    }
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(error = "Unable to load settings. Restart the app and retry.")
            }
        }
    }

    fun refresh() {
        val config = mutableState.value.configuration
        if (!config.isReady || mutableState.value.sending) return
        val generation = ++refreshGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                val destination = gateway.destination(config)
                if (generation == refreshGeneration) mutableState.value = mutableState.value.copy(destination = destination)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (generation == refreshGeneration) mutableState.value = mutableState.value.copy(error = userMessage(error))
            } finally {
                if (generation == refreshGeneration) mutableState.value = mutableState.value.copy(loading = false)
            }
        }
    }

    fun updateQuery(value: String) {
        savedState["query"] = value
        searchGeneration++
        searchJob?.cancel()
        mutableState.value = mutableState.value.copy(query = value, results = emptyList(), searching = false)
        if (value.length < 3) return
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            search()
        }
    }

    fun search() {
        val query = mutableState.value.query
        if (query.isBlank()) return
        val generation = ++searchGeneration
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(searching = true, error = null)
            try {
                val results = resolver.search(query)
                if (generation == searchGeneration) mutableState.value = mutableState.value.copy(results = results,
                    error = if (results.isEmpty()) "No destination found. Enter an address or exact coordinates." else null)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (generation == searchGeneration) mutableState.value = mutableState.value.copy(error = userMessage(error))
            } finally {
                if (generation == searchGeneration) mutableState.value = mutableState.value.copy(searching = false)
            }
        }
    }

    fun send(place: PlaceResult?) {
        val config = mutableState.value.configuration
        if (!config.isReady || mutableState.value.sending) return
        refreshGeneration++
        refreshJob?.cancel()
        mutableState.value = mutableState.value.copy(sending = true, loading = false, error = null)
        commandJob = viewModelScope.launch {
            try {
                if (place == null) gateway.clearDestination(config) else gateway.setDestination(config, place)
                mutableState.value = mutableState.value.copy(destination = place?.let {
                    Destination(it.coordinates.latitude, it.coordinates.longitude, it.label)
                })
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { mutableState.value = mutableState.value.copy(error = userMessage(error))
            } finally { mutableState.value = mutableState.value.copy(sending = false) }
        }
    }
}
