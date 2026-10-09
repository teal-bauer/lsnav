package dev.ligustah.lsnav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationService
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class Screen { Navigation, Settings }

class SettingsActivity : ComponentActivity() {
    private val settingsModel: SettingsViewModel by viewModels()
    private val authorizationService by lazy { AuthorizationService(this) }
    private val authorizationResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        settingsModel.completeLogin(it.data)
    }

    private fun signIn(baseUrl: String, clientId: String) {
        lifecycleScope.launch {
            try {
                authorizationResult.launch(OAuthManager.get(this@SettingsActivity).authorizationIntent(baseUrl, clientId, authorizationService))
            } catch (error: kotlinx.coroutines.CancellationException) { throw error
            } catch (error: Exception) { settingsModel.authError(error) }
        }
    }

    override fun onDestroy() { authorizationService.dispose(); super.onDestroy() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsModel.state.collectAsStateWithLifecycle()
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                var screen by rememberSaveable { mutableStateOf(Screen.Navigation) }
                val onboarding = !settings.profile.onboardingComplete
                val destination = if (onboarding) Screen.Settings else screen
                BackHandler(enabled = destination == Screen.Settings && !onboarding) { screen = Screen.Navigation }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (destination) {
                        Screen.Navigation -> NavigationScreen(onSettings = { screen = Screen.Settings })
                        Screen.Settings -> SettingsScreen(settingsModel, ::signIn) {
                            screen = Screen.Navigation
                            if (onboarding && settings.profile.onboardingComplete) screen = Screen.Navigation
                        }
                    }
                }
            }
        }
    }
}
