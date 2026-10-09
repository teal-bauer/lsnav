package dev.ligustah.lsnav

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

data class AppConfiguration(
    val token: String = "",
    val baseUrl: String = AppSettings.DEFAULT_BASE_URL,
    val scooterId: Long? = null,
    val scooterName: String = "",
    val oauthSessionId: String? = null,
    val oauthClientId: String = OAuthManager.DEFAULT_CLIENT_ID
) {
    val isAuthenticated: Boolean get() = token.isNotBlank() || oauthSessionId != null
    val isReady: Boolean get() = isAuthenticated && scooterId != null
    val accountKey: AppConfiguration get() = copy(scooterId = null, scooterName = "")
}

class AppSettings(context: Context) {
    private val context = context.applicationContext
    companion object {
        val TOKEN_KEY = stringPreferencesKey("api_token")
        val BASE_URL_KEY = stringPreferencesKey("base_url")
        val SCOOTER_ID_KEY = longPreferencesKey("scooter_id")
        val SCOOTER_NAME_KEY = stringPreferencesKey("scooter_name")
        val OAUTH_SESSION_KEY = stringPreferencesKey("oauth_session")
        val OAUTH_CLIENT_KEY = stringPreferencesKey("oauth_client")
        val ONBOARDING_KEY = booleanPreferencesKey("onboarding_complete")
        
        const val DEFAULT_BASE_URL = "https://sunshine.rescoot.org"
    }

    val profile: Flow<AppProfile> = context.dataStore.data.map {
        val configuration = AppConfiguration(it[TOKEN_KEY] ?: "", it[BASE_URL_KEY] ?: DEFAULT_BASE_URL, it[SCOOTER_ID_KEY], it[SCOOTER_NAME_KEY] ?: "",
            it[OAUTH_SESSION_KEY], it[OAUTH_CLIENT_KEY] ?: OAuthManager.DEFAULT_CLIENT_ID)
        AppProfile(configuration, it[ONBOARDING_KEY] ?: configuration.isAuthenticated)
    }
    val configuration: Flow<AppConfiguration> = profile.map { it.configuration }

    val token: Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }
    val baseUrl: Flow<String> = context.dataStore.data.map { it[BASE_URL_KEY] ?: DEFAULT_BASE_URL }
    val scooterId: Flow<Long?> = context.dataStore.data.map { it[SCOOTER_ID_KEY] }
    val scooterName: Flow<String?> = context.dataStore.data.map { it[SCOOTER_NAME_KEY] }

    suspend fun saveSettings(token: String, baseUrl: String, scooterId: Long?, scooterName: String) =
        saveConfiguration(AppConfiguration(token, baseUrl, scooterId, scooterName))

    suspend fun saveConfiguration(configuration: AppConfiguration) = save(configuration, null)
    suspend fun saveProfile(profile: AppProfile) = save(profile.configuration, profile.onboardingComplete)

    private suspend fun save(configuration: AppConfiguration, onboardingComplete: Boolean?) {
        val (token, baseUrl, scooterId, scooterName) = configuration
        context.dataStore.edit {
            if (!configuration.isAuthenticated) it[ONBOARDING_KEY] = false
            else if (onboardingComplete != null) it[ONBOARDING_KEY] = onboardingComplete
            if (configuration.oauthSessionId == null) it.remove(OAUTH_SESSION_KEY) else it[OAUTH_SESSION_KEY] = configuration.oauthSessionId
            it[OAUTH_CLIENT_KEY] = configuration.oauthClientId
            it[TOKEN_KEY] = token
            it[BASE_URL_KEY] = baseUrl
            if (scooterId == null) {
                it.remove(SCOOTER_ID_KEY)
                it.remove(SCOOTER_NAME_KEY)
            } else {
                it[SCOOTER_ID_KEY] = scooterId
                it[SCOOTER_NAME_KEY] = scooterName
            }
        }
    }
}
