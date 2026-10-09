package dev.ligustah.lsnav

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

 data class NavigationState(
    val configuration: AppConfiguration = AppConfiguration(),
    val scooters: RemoteData<List<ScooterSummary>> = RemoteData(),
    val destination: RemoteData<NavigationTarget> = RemoteData(),
    val savedLocations: RemoteData<List<PlaceResult>> = RemoteData(),
    val personalPlaces: RemoteData<PersonalPlaces> = RemoteData(),
    val sending: Boolean = false,
    val query: String = "",
    val results: List<PlaceResult> = emptyList(),
    val searching: Boolean = false,
    val error: String? = null
)

class NavigationViewModel @JvmOverloads constructor(
    application: Application,
    private val savedState: SavedStateHandle,
    private val gateway: NavigationGateway = NavigationRepository.get(application),
    private val resolver: CoordinateResolver = CoordinateResolver(application),
    private val configurations: kotlinx.coroutines.flow.Flow<AppConfiguration> = AppSettings(application).configuration
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(NavigationState(query = savedState["query"] ?: ""))
    val state: StateFlow<NavigationState> = mutableState
    private var storedConfiguration: AppConfiguration? = null
    private var scootersJob: Job? = null
    private var destinationJob: Job? = null
    private var locationsJob: Job? = null
    private var personalPlacesJob: Job? = null
    private var commandJob: Job? = null
    private var searchJob: Job? = null
    private var scooterGeneration = 0
    private var destinationGeneration = 0
    private var locationsGeneration = 0
    private var personalGeneration = 0
    private var searchGeneration = 0

    init {
        viewModelScope.launch {
            try {
                configurations.collect { config ->
                    if (config != storedConfiguration) {
                        val old = storedConfiguration
                        storedConfiguration = config
                        cancelResources()
                        commandJob?.cancel()
                        mutableState.value = mutableState.value.copy(
                            configuration = config, sending = false,
                            scooters = if (old?.accountKey == config.accountKey) mutableState.value.scooters else RemoteData(),
                            destination = RemoteData(), savedLocations = RemoteData(),
                            personalPlaces = if (old?.accountKey == config.accountKey) mutableState.value.personalPlaces else RemoteData(),
                            error = null
                        )
                        if (config.isAuthenticated) {
                            fetchScooters()
                            refreshPersonalPlaces()
                            refresh()
                        }
                    }
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(error = "Unable to load settings. Restart the app and retry.")
            }
        }
    }

    private fun cancelResources() {
        scooterGeneration++; scootersJob?.cancel()
        destinationGeneration++; destinationJob?.cancel()
        locationsGeneration++; locationsJob?.cancel()
        personalGeneration++; personalPlacesJob?.cancel()
    }

    fun fetchScooters(forceRefresh: Boolean = false) {
        val config = storedConfiguration ?: return
        if (!config.isAuthenticated) return
        val generation = ++scooterGeneration
        scootersJob?.cancel()
        mutableState.value = mutableState.value.copy(scooters = mutableState.value.scooters.refreshing())
        scootersJob = viewModelScope.launch {
            try {
                val value = gateway.scooters(config, forceRefresh).distinctBy { it.id }
                if (generation == scooterGeneration && storedConfiguration?.accountKey == config.accountKey) {
                    val selectedExists = config.scooterId == null || value.any { it.id == config.scooterId }
                    mutableState.value = mutableState.value.copy(
                        scooters = RemoteData.loaded(value),
                        configuration = if (selectedExists) mutableState.value.configuration else config.copy(scooterId = null, scooterName = ""),
                        destination = if (selectedExists) mutableState.value.destination else RemoteData(),
                        savedLocations = if (selectedExists) mutableState.value.savedLocations else RemoteData()
                    )
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (generation == scooterGeneration) mutableState.value = mutableState.value.copy(scooters = mutableState.value.scooters.failed(userMessage(error)))
            } finally {
                if (generation == scooterGeneration) mutableState.value = mutableState.value.copy(scooters = mutableState.value.scooters.copy(loading = false))
            }
        }
    }

    fun selectScooter(scooter: ScooterSummary) {
        val state = mutableState.value
        if (state.sending || scooter !in state.scooters.value.orEmpty()) return
        val config = state.configuration.copy(scooterId = scooter.id, scooterName = scooter.name)
        if (config == state.configuration) return
        mutableState.value = state.copy(configuration = config, destination = RemoteData(), savedLocations = RemoteData(), error = null)
        destinationGeneration++; destinationJob?.cancel()
        locationsGeneration++; locationsJob?.cancel()
        refresh()
    }

    fun refresh() {
        val config = mutableState.value.configuration
        if (!config.isReady || mutableState.value.sending) return
        refreshDestination(config)
        refreshSavedLocations(config)
    }

    private fun refreshDestination(config: AppConfiguration) {
        val generation = ++destinationGeneration
        destinationJob?.cancel()
        mutableState.value = mutableState.value.copy(destination = mutableState.value.destination.refreshing())
        destinationJob = viewModelScope.launch {
            try {
                val value = gateway.destination(config)
                if (generation == destinationGeneration && mutableState.value.configuration == config) mutableState.value = mutableState.value.copy(destination = RemoteData.loaded(value))
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (generation == destinationGeneration) mutableState.value = mutableState.value.copy(destination = mutableState.value.destination.failed(userMessage(error)))
            } finally {
                if (generation == destinationGeneration) mutableState.value = mutableState.value.copy(destination = mutableState.value.destination.copy(loading = false))
            }
        }
    }

    private fun refreshSavedLocations(config: AppConfiguration) {
        val generation = ++locationsGeneration
        locationsJob?.cancel()
        mutableState.value = mutableState.value.copy(savedLocations = mutableState.value.savedLocations.refreshing())
        locationsJob = viewModelScope.launch {
            try {
                val value = gateway.savedLocations(config)
                if (generation == locationsGeneration && mutableState.value.configuration == config) mutableState.value = mutableState.value.copy(savedLocations = RemoteData.loaded(value))
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (generation == locationsGeneration) mutableState.value = mutableState.value.copy(savedLocations = mutableState.value.savedLocations.failed(userMessage(error)))
            } finally {
                if (generation == locationsGeneration) mutableState.value = mutableState.value.copy(savedLocations = mutableState.value.savedLocations.copy(loading = false))
            }
        }
    }

    fun refreshPersonalPlaces(forceRefresh: Boolean = false) {
        val config = storedConfiguration ?: return
        if (!config.isAuthenticated) return
        val generation = ++personalGeneration
        personalPlacesJob?.cancel()
        mutableState.value = mutableState.value.copy(personalPlaces = mutableState.value.personalPlaces.refreshing())
        personalPlacesJob = viewModelScope.launch {
            try {
                val value = gateway.personalPlaces(config, forceRefresh)
                if (generation == personalGeneration && storedConfiguration?.accountKey == config.accountKey) mutableState.value = mutableState.value.copy(personalPlaces = RemoteData.loaded(value))
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (generation == personalGeneration) mutableState.value = mutableState.value.copy(personalPlaces = mutableState.value.personalPlaces.failed(userMessage(error)))
            } finally {
                if (generation == personalGeneration) mutableState.value = mutableState.value.copy(personalPlaces = mutableState.value.personalPlaces.copy(loading = false))
            }
        }
    }

    fun updateQuery(value: String) {
        savedState["query"] = value
        searchGeneration++
        searchJob?.cancel()
        mutableState.value = mutableState.value.copy(query = value, results = emptyList(), searching = false)
        if (value.length < 3) return
        searchJob = viewModelScope.launch { kotlinx.coroutines.delay(500); search() }
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

    fun send(place: PlaceResult?, expectedConfiguration: AppConfiguration = mutableState.value.configuration) {
        val config = mutableState.value.configuration
        if (config != expectedConfiguration) {
            mutableState.value = mutableState.value.copy(error = "Scooter or settings changed. Confirm the destination again.")
            return
        }
        if (!config.isReady || mutableState.value.sending) return
        if (place != null && !place.coordinates.isValid()) return
        destinationGeneration++; destinationJob?.cancel()
        locationsGeneration++; locationsJob?.cancel()
        mutableState.value = mutableState.value.copy(sending = true, error = null)
        commandJob = viewModelScope.launch {
            try {
                if (place == null) gateway.clearDestination(config) else gateway.setDestination(config, place)
                if (mutableState.value.configuration == config) refreshDestination(config)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { mutableState.value = mutableState.value.copy(error = userMessage(error))
            } finally { mutableState.value = mutableState.value.copy(sending = false) }
        }
    }
}
