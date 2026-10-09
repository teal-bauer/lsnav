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
    private val gateway: NavigationGateway = NavigationRepository(application)
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
                    savedState["name"] ?: config.scooterName,
                    if (savedState.contains("oauthSession")) savedState["oauthSession"] else config.oauthSessionId,
                    savedState["clientId"] ?: config.oauthClientId)
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
        savedState["oauthSession"] = value.oauthSessionId
        savedState["clientId"] = value.oauthClientId
        mutableState.value = mutableState.value.copy(draft = value, message = null, error = null)
    }

    fun updateToken(value: String) {
        if (mutableState.value.saving) return
        draft(mutableState.value.draft.copy(token = value, oauthSessionId = null, scooterId = null, scooterName = ""))
        mutableState.value = mutableState.value.copy(scooters = emptyList())
    }
    fun updateUrl(value: String) {
        if (mutableState.value.saving) return
        draft(mutableState.value.draft.copy(baseUrl = value, oauthSessionId = null, scooterId = null, scooterName = ""))
        mutableState.value = mutableState.value.copy(scooters = emptyList())
    }
    fun chooseOnDemand() {
        if (mutableState.value.saving) return
        draft(mutableState.value.draft.copy(scooterId = null, scooterName = ""))
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
                require(config.token.isNotBlank() || config.oauthSessionId != null) { "Sign in or enter an API token" }
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

    fun updateClientId(value: String) {
        draft(mutableState.value.draft.copy(oauthClientId = value, oauthSessionId = null, scooterId = null, scooterName = ""))
    }

    fun authError(error: Exception) {
        mutableState.value = mutableState.value.copy(error = userMessage(error), loading = false)
    }

    fun completeLogin(intent: android.content.Intent?) {
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val config = OAuthManager.get(getApplication()).complete(intent)
                settings.saveConfiguration(config)
                draft(config)
                mutableState.value = mutableState.value.copy(scooters = emptyList(), loading = false, message = "Signed in. Choose a default scooter or select one when navigating.")
                fetch()
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { authError(error) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            settings.saveConfiguration(AppConfiguration(baseUrl = mutableState.value.draft.baseUrl))
            OAuthManager.get(getApplication()).clear()
            draft(AppConfiguration(baseUrl = mutableState.value.draft.baseUrl))
            mutableState.value = mutableState.value.copy(scooters = emptyList(), message = "Signed out of this app.")
        }
    }

    fun save() {
        if (mutableState.value.loading || mutableState.value.saving) return
        val config = mutableState.value.draft
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val url = ApiClientProvider.normalizeBaseUrl(config.baseUrl)
                require(config.token.isNotBlank() || config.oauthSessionId != null) { "Sign in or enter an API token" }
                settings.saveConfiguration(config.copy(token = config.token.trim(), baseUrl = url))
                draft(config.copy(token = config.token.trim(), baseUrl = url))
                mutableState.value = mutableState.value.copy(message = "Settings saved.")
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { mutableState.value = mutableState.value.copy(error = userMessage(error))
            } finally { mutableState.value = mutableState.value.copy(saving = false) }
        }
    }
}
