package dev.ligustah.lsnav

import android.content.Context
import androidx.datastore.preferences.core.edit
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
    val scooterName: String = ""
) {
    val isReady: Boolean get() = token.isNotBlank() && scooterId != null
}

class AppSettings(context: Context) {
    private val context = context.applicationContext
    companion object {
        val TOKEN_KEY = stringPreferencesKey("api_token")
        val BASE_URL_KEY = stringPreferencesKey("base_url")
        val SCOOTER_ID_KEY = longPreferencesKey("scooter_id")
        val SCOOTER_NAME_KEY = stringPreferencesKey("scooter_name")
        
        const val DEFAULT_BASE_URL = "https://sunshine.rescoot.org"
    }

    val configuration: Flow<AppConfiguration> = context.dataStore.data.map {
        AppConfiguration(it[TOKEN_KEY] ?: "", it[BASE_URL_KEY] ?: DEFAULT_BASE_URL, it[SCOOTER_ID_KEY], it[SCOOTER_NAME_KEY] ?: "")
    }

    val token: Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }
    val baseUrl: Flow<String> = context.dataStore.data.map { it[BASE_URL_KEY] ?: DEFAULT_BASE_URL }
    val scooterId: Flow<Long?> = context.dataStore.data.map { it[SCOOTER_ID_KEY] }
    val scooterName: Flow<String?> = context.dataStore.data.map { it[SCOOTER_NAME_KEY] }

    suspend fun saveSettings(token: String, baseUrl: String, scooterId: Long?, scooterName: String) {
        context.dataStore.edit {
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
