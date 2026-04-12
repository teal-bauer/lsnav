package dev.ligustah.lsnav

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

data class Coordinates(val latitude: Double, val longitude: Double)

class CoordinateResolver(private val context: Context, private val okHttpClient: OkHttpClient = OkHttpClient()) {

    suspend fun resolve(text: String): Coordinates? = withContext(Dispatchers.IO) {
        val cleanText = MapsIntentParser.extractUrlOrText(text)
        val uri = Uri.parse(cleanText)
        
        val parsed = MapsIntentParser.extractCoordinates(uri)
        if (parsed != null) return@withContext parsed

        var actualUrl = cleanText
        if (cleanText.contains("maps.app.goo.gl") || cleanText.contains("goo.gl/maps")) {
            try {
                // If it's a shortlink, try to resolve it first
                val request = Request.Builder().url(cleanText).head().build() 
                val response = okHttpClient.newCall(request).execute()
                actualUrl = response.request.url.toString()
                
                val redirectedParsed = MapsIntentParser.extractCoordinates(Uri.parse(actualUrl))
                if (redirectedParsed != null) return@withContext redirectedParsed
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val urlPlaceName = MapsIntentParser.extractPlaceName(Uri.parse(actualUrl))
            val textWithoutUrl = text.replace(cleanText, "").replace('\n', ' ').trim()

            val queries = listOfNotNull(urlPlaceName, textWithoutUrl.takeIf { it.isNotBlank() })
            for (query in queries) {
                val addresses = geocoder.getFromLocationName(query, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    return@withContext Coordinates(addr.latitude, addr.longitude)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        null
    }

    suspend fun searchLocalAddress(query: String): List<android.location.Address> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            return@withContext geocoder.getFromLocationName(query, 5) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }
}
