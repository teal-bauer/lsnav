package dev.ligustah.lsnav

import android.net.Uri
import android.util.Patterns

object MapsIntentParser {
    const val MAX_INPUT_LENGTH = 16_384
    private val pair = Regex("^\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*,\\s*([-+]?\\d+(?:\\.\\d+)?)(?:\\s*\\([^)]*\\))?\\s*$")
    private val routePair = Regex("!1d([-+]?\\d+(?:\\.\\d+)?)!2d([-+]?\\d+(?:\\.\\d+)?)(?=!|$)")
    private val pinPair = Regex("!3d([-+]?\\d+(?:\\.\\d+)?)!4d([-+]?\\d+(?:\\.\\d+)?)(?=!|$)")

    fun parsePair(text: String): Coordinates? {
        val match = pair.matchEntire(text) ?: return null
        return coordinates(match.groupValues[1], match.groupValues[2])
    }

    private fun coordinates(lat: String, lon: String): Coordinates? {
        val latitude = lat.toDoubleOrNull() ?: return null
        val longitude = lon.toDoubleOrNull() ?: return null
        return Coordinates(latitude, longitude).takeIf { it.isValid() }
    }

    fun extractCoordinates(uri: Uri): Coordinates? {
        if (uri.toString().length > MAX_INPUT_LENGTH) return null
        if (uri.scheme == "geo") {
            val value = uri.encodedSchemeSpecificPart
            val query = value.substringAfter('?', "")
            val q = Uri.parse("https://geo.invalid/?$query").getQueryParameter("q")
            if (q != null) return parsePair(q)
            return parsePair(value.substringBefore('?').substringBefore(';').split(',').take(2).joinToString(","))
        }
        if (!uri.isHierarchical) return null
        for (key in listOf("destination", "query", "q", "daddr")) {
            uri.getQueryParameter(key)?.let { parsePair(it)?.let { result -> return result } }
        }
        val data = uri.pathSegments.firstOrNull { it.startsWith("data=") }
            ?: uri.getQueryParameter("data")
        if (data != null) {
            if ("dir" in uri.pathSegments) {
                routePair.findAll(data).lastOrNull()?.let {
                    coordinates(it.groupValues[2], it.groupValues[1])?.let { result -> return result }
                }
            }
            pinPair.findAll(data).lastOrNull()?.let {
                coordinates(it.groupValues[1], it.groupValues[2])?.let { result -> return result }
            }
        }
        val place = uri.pathSegments.indexOf("place")
        if (place >= 0) uri.pathSegments.getOrNull(place + 1)?.let { parsePair(it)?.let { result -> return result } }
        val direction = uri.pathSegments.indexOf("dir")
        if (direction >= 0) {
            val endpoint = uri.pathSegments.drop(direction + 1).lastOrNull { !it.startsWith("@") && !it.startsWith("data=") }
            endpoint?.let { parsePair(it)?.let { result -> return result } }
        }
        if (uri.host == "maps.apple.com") {
            uri.getQueryParameter("ll")?.let { parsePair(it)?.let { result -> return result } }
        }
        if (uri.host?.let { it == "openstreetmap.org" || it == "www.openstreetmap.org" } == true) {
            val lat = uri.getQueryParameter("mlat")
            val lon = uri.getQueryParameter("mlon")
            if (lat != null && lon != null) return coordinates(lat, lon)
        }
        // Viewport coordinates do not identify the place or route destination.
        return null
    }

    fun extractPlaceName(uri: Uri): String? {
        if (uri.toString().length > MAX_INPUT_LENGTH) return null
        if (uri.scheme == "geo") {
            val query = uri.encodedSchemeSpecificPart.substringAfter('?', "")
            return Uri.parse("https://geo.invalid/?$query").getQueryParameter("q")
                ?.takeIf { parsePair(it) == null && it.isNotBlank() }
        }
        if (!uri.isHierarchical) return null
        val index = uri.pathSegments.indexOf("place")
        if (index >= 0) {
            uri.pathSegments.getOrNull(index + 1)?.replace('+', ' ')?.let {
                if (parsePair(it) == null && it.isNotBlank()) return it
            }
        }
        for (key in listOf("destination", "query", "q", "daddr")) {
            uri.getQueryParameter(key)?.let {
                if (parsePair(it) == null && it.isNotBlank()) return it
            }
        }
        val direction = uri.pathSegments.indexOf("dir")
        if (direction >= 0) {
            val endpoint = uri.pathSegments.drop(direction + 1).lastOrNull { !it.startsWith("@") && !it.startsWith("data=") }
            if (!endpoint.isNullOrBlank() && parsePair(endpoint) == null) return endpoint.replace('+', ' ')
        }
        return null
    }

    fun extractUrlOrText(text: String): String {
        if (text.length > MAX_INPUT_LENGTH) return ""
        val trimmed = text.trim()
        if (trimmed.startsWith("geo:")) return trimmed
        val matcher = Patterns.WEB_URL.matcher(trimmed)
        return if (matcher.find()) matcher.group() ?: trimmed else trimmed
    }
}
