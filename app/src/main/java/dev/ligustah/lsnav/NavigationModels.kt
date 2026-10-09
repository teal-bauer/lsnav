package dev.ligustah.lsnav

import java.util.Locale

data class Coordinates(val latitude: Double, val longitude: Double) {
    fun isValid() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
    fun display() = String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude)
}

data class PlaceResult(val coordinates: Coordinates, val label: String)
data class ScooterSummary(val id: Long, val name: String, val online: Boolean? = null, val state: String? = null)
data class NavigationTarget(val place: PlaceResult? = null, val multiStopSupported: Boolean = false)
data class SavedPlace(val id: Long, val place: PlaceResult)
data class PersonalPlaces(val favorites: List<SavedPlace> = emptyList(), val recent: List<SavedPlace> = emptyList())
data class AppProfile(val configuration: AppConfiguration = AppConfiguration(), val onboardingComplete: Boolean = false)
data class DestinationChange(val configuration: AppConfiguration)

data class RemoteData<T>(
    val value: T? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val updatedAt: Long = 0
) {
    fun stale(ageMillis: Long) = !loading && System.currentTimeMillis() - updatedAt >= ageMillis
    fun refreshing() = copy(loading = true, error = null)
    fun failed(message: String) = copy(loading = false, error = message)
    companion object {
        fun <T> loaded(value: T) = RemoteData(value = value, updatedAt = System.currentTimeMillis())
    }
}
