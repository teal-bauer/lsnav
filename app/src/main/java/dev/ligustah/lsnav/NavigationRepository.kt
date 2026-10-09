package dev.ligustah.lsnav

import dev.ligustah.lsnav.api.generated.models.Destination
import dev.ligustah.lsnav.api.generated.models.DestinationInput
import dev.ligustah.lsnav.api.generated.models.Scooter
import kotlinx.coroutines.suspendCancellableCoroutine
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.infrastructure.ServerException
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface NavigationGateway {
    suspend fun scooters(configuration: AppConfiguration): List<Scooter>
    suspend fun savedLocations(configuration: AppConfiguration): List<PlaceResult> = emptyList()
    suspend fun destination(configuration: AppConfiguration): Destination
    suspend fun setDestination(configuration: AppConfiguration, place: PlaceResult)
    suspend fun clearDestination(configuration: AppConfiguration)
}

class NavigationRepository(
    private val context: android.content.Context? = null,
    private val providerFactory: (AppConfiguration) -> ApiClientProvider = { ApiClientProvider(it.baseUrl, it.token) }
) : NavigationGateway {
    override suspend fun scooters(configuration: AppConfiguration) = request(configuration) { it.getScootersApi().listScooters() }
    override suspend fun savedLocations(configuration: AppConfiguration) = request(configuration) {
        it.getNavigationApi().getSavedLocations(requireNotNull(configuration.scooterId)).locations.mapNotNull { location ->
            val coordinates = Coordinates(location.latitude, location.longitude)
            if (coordinates.isValid()) PlaceResult(coordinates, location.label) else null
        }
    }
    override suspend fun destination(configuration: AppConfiguration) = request(configuration) {
        it.getNavigationApi().getDestination(requireNotNull(configuration.scooterId))
    }
    override suspend fun setDestination(configuration: AppConfiguration, place: PlaceResult) {
        require(place.coordinates.isValid()) { "Invalid destination coordinates" }
        request(configuration) {
            it.getNavigationApi().setDestination(requireNotNull(configuration.scooterId),
                DestinationInput(place.coordinates.latitude, place.coordinates.longitude, place.label))
        }
    }
    override suspend fun clearDestination(configuration: AppConfiguration) {
        request(configuration) { it.getNavigationApi().clearDestination(requireNotNull(configuration.scooterId)) }
    }

    private suspend fun <T> request(configuration: AppConfiguration, block: (ApiClientProvider) -> T): T {
        if (configuration.oauthSessionId == null) return execute(configuration, block)
        val manager = OAuthManager.get(context ?: throw OAuthLoginRequired())
        val token = manager.accessToken(configuration)
        return try {
            execute(configuration.copy(token = token), block)
        } catch (error: ClientException) {
            if (error.statusCode != 401) throw error
            execute(configuration.copy(token = manager.accessToken(configuration, forceRefresh = true)), block)
        }
    }

    private suspend fun <T> execute(configuration: AppConfiguration, block: (ApiClientProvider) -> T): T {
        val provider = providerFactory(configuration)
        return suspendCancellableCoroutine { continuation ->
            val task = FutureTask {
                if (continuation.isActive) {
                    try {
                        val result = block(provider)
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
            continuation.invokeOnCancellation {
                provider.cancel()
                task.cancel(true)
            }
            executor.execute(task)
        }
    }

    companion object {
        private val executor = Executors.newFixedThreadPool(4)
    }
}

fun userMessage(error: Exception): String = when (error) {
    is OAuthLoginRequired -> "Your Sunshine login has expired or been revoked. Sign in again in Settings."
    is ClientException -> when (error.statusCode) {
        401 -> "Your API token is expired or invalid. Update it in Settings."
        403 -> "This token or account lacks permission for this scooter. Check its sharing permissions."
        404 -> "The scooter was not found. Fetch and select it again in Settings."
        422 -> "The destination or command could not be accepted. Check the coordinates and scooter connectivity."
        429 -> "Too many requests. Wait a moment and retry."
        else -> "The server rejected the request. Check Settings and retry."
    }
    is ServerException -> "The server is unavailable. Please retry later."
    is IllegalArgumentException -> error.message ?: "Check the input and retry."
    is IOException -> "Unable to connect or resolve this location. Check the network and retry."
    else -> "The request failed. Please retry."
}
