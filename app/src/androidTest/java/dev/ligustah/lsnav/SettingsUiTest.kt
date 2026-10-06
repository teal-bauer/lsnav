package dev.ligustah.lsnav

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class SettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<SettingsActivity>()

    @Test fun settingsDraftSurvivesTabChangesAndRecreation() {
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("HTTPS server URL") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        val field = compose.onNodeWithText("HTTPS server URL")
        field.performTextClearance()
        field.performTextInput("https://draft.example.org")
        compose.onNodeWithText("Navigation", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("HTTPS server URL").assertTextContains("https://draft.example.org")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("HTTPS server URL").assertTextContains("https://draft.example.org")
    }
}
