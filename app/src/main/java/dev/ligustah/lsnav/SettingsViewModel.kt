package dev.ligustah.lsnav

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.ligustah.lsnav.api.generated.models.Scooter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsState(
    val draft: AppConfiguration = AppConfiguration(),
    val scooters: List<Scooter> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class SettingsViewModel @JvmOverloads constructor(
    application: Application,
    private val savedState: SavedStateHandle,
    private val gateway: NavigationGateway = NavigationRepository()
) : AndroidViewModel(application) {
    private val settings = AppSettings(application)
    private val mutableState = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = mutableState

    init {
        viewModelScope.launch {
            try {
                val config = settings.configuration.first()
                val draft = AppConfiguration(savedState["token"] ?: config.token, savedState["url"] ?: config.baseUrl,
                    if (savedState.contains("url")) savedState["scooter"] else config.scooterId,
                    savedState["name"] ?: config.scooterName)
                mutableState.value = SettingsState(draft = draft, loading = false)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { mutableState.value = mutableState.value.copy(loading = false, error = "Unable to load settings. Restart the app and retry.") }
        }
    }

    private fun draft(value: AppConfiguration) {
        savedState["token"] = value.token
        savedState["url"] = value.baseUrl
        savedState["scooter"] = value.scooterId
        savedState["name"] = value.scooterName
        mutableState.value = mutableState.value.copy(draft = value, message = null, error = null)
    }

    fun updateToken(value: String) {
        if (mutableState.value.saving) return
        draft(mutableState.value.draft.copy(token = value, scooterId = null, scooterName = ""))
        mutableState.value = mutableState.value.copy(scooters = emptyList())
    }
    fun updateUrl(value: String) {
        if (mutableState.value.saving) return
        draft(mutableState.value.draft.copy(baseUrl = value, scooterId = null, scooterName = ""))
        mutableState.value = mutableState.value.copy(scooters = emptyList())
    }
    fun select(scooter: Scooter) {
        if (mutableState.value.saving) return
        draft(mutableState.value.draft.copy(scooterId = scooter.id, scooterName = scooter.name))
    }

    fun fetch() {
        if (mutableState.value.loading || mutableState.value.saving) return
        val config = mutableState.value.draft
        mutableState.value = mutableState.value.copy(loading = true, error = null, scooters = emptyList())
        viewModelScope.launch {
            try {
                ApiClientProvider.normalizeBaseUrl(config.baseUrl)
                require(config.token.isNotBlank()) { "Enter an API token" }
                val scooters = gateway.scooters(config)
                if (config == mutableState.value.draft) {
                    mutableState.value = mutableState.value.copy(scooters = scooters)
                    if (scooters.none { it.id == config.scooterId }) draft(config.copy(scooterId = null, scooterName = ""))
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (config == mutableState.value.draft) mutableState.value = mutableState.value.copy(error = userMessage(error))
            } finally { mutableState.value = mutableState.value.copy(loading = false) }
        }
    }

    fun save() {
        if (mutableState.value.loading || mutableState.value.saving) return
        val config = mutableState.value.draft
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val url = ApiClientProvider.normalizeBaseUrl(config.baseUrl)
                require(config.token.isNotBlank()) { "Enter an API token" }
                settings.saveSettings(config.token.trim(), url, config.scooterId, config.scooterName)
                draft(config.copy(token = config.token.trim(), baseUrl = url))
                mutableState.value = mutableState.value.copy(message = "Settings saved.")
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { mutableState.value = mutableState.value.copy(error = userMessage(error))
            } finally { mutableState.value = mutableState.value.copy(saving = false) }
        }
    }
}
