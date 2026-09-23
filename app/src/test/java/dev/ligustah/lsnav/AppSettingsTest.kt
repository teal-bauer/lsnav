package dev.ligustah.lsnav

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppSettingsTest {
    @Test
    fun `saving credentials without a scooter clears the previous selection`() = runBlocking {
        val settings = AppSettings(RuntimeEnvironment.getApplication())
        settings.saveSettings("old-token", "https://old.example", 42, "Old scooter")
        settings.saveSettings("new-token", "https://new.example", null, "")

        assertEquals("new-token", settings.token.first())
        assertEquals("https://new.example", settings.baseUrl.first())
        assertNull(settings.scooterId.first())
        assertNull(settings.scooterName.first())
    }
}
