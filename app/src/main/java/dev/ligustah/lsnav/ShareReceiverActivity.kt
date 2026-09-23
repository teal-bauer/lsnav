package dev.ligustah.lsnav

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.ligustah.lsnav.api.generated.models.DestinationInput

class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (text != null) {
                handleShare(text)
                return
            }
        }
        
        Toast.makeText(this, getString(R.string.share_handling_failed), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun handleShare(sharedText: String) {
        val appSettings = AppSettings(this)
        val resolver = CoordinateResolver(this)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val coords = resolver.resolve(sharedText)
                if (coords != null) {
                    val scooterId = appSettings.scooterId.firstOrNull()
                    val token = appSettings.token.firstOrNull()
                    val baseUrl = appSettings.baseUrl.first()

                    if (scooterId != null && token != null) {
                        val api = ApiClientProvider(baseUrl, token).getNavigationApi()
                        withContext(Dispatchers.IO) {
                            val dest = DestinationInput(
                                latitude = coords.latitude,
                                longitude = coords.longitude
                            )
                            api.setDestination(scooterId, dest)
                        }
                        Toast.makeText(this@ShareReceiverActivity, getString(R.string.share_destination_sent), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@ShareReceiverActivity, "No scooter configured in settings.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@ShareReceiverActivity, getString(R.string.share_handling_failed), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@ShareReceiverActivity, getString(R.string.share_destination_failed), Toast.LENGTH_LONG).show()
            } finally {
                finish()
            }
        }
    }
}
