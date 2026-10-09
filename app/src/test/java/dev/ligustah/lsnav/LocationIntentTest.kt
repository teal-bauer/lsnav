package dev.ligustah.lsnav

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationIntentTest {
    @Test fun `Calendar geo VIEW opens destination confirmation`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=Alexanderplatz%2C%20Berlin"))
        assertEquals("geo:0,0?q=Alexanderplatz%2C%20Berlin", MapsIntentParser.extractIntentText(intent))
        assertEquals("Alexanderplatz, Berlin", MapsIntentParser.extractPlaceName(intent.data!!))
        val handlers = RuntimeEnvironment.getApplication().packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        assertTrue(handlers.any { it.activityInfo.name == "dev.ligustah.lsnav.ShareReceiverActivity" })
    }
    @Test fun `geo coordinates remain destinations rather than placeholder origin`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=52.52,13.41(Berlin)"))
        assertEquals(Coordinates(52.52, 13.41), MapsIntentParser.extractCoordinates(Uri.parse(MapsIntentParser.extractIntentText(intent))))
    }
    @Test fun `text sharing remains supported`() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Berlin")
        assertEquals("Berlin", MapsIntentParser.extractIntentText(intent))
    }
    @Test fun `unrelated intents and oversized payloads are rejected`() {
        assertNull(MapsIntentParser.extractIntentText(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org"))))
        assertNull(MapsIntentParser.extractIntentText(Intent(Intent.ACTION_SEND).setType("image/png")))
        assertNull(MapsIntentParser.extractIntentText(Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + "a".repeat(MapsIntentParser.MAX_INPUT_LENGTH)))))
    }
}
