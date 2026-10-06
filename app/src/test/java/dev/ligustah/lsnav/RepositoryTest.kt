package dev.ligustah.lsnav

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import org.openapitools.client.infrastructure.ClientException
import java.util.concurrent.TimeUnit

class RepositoryTest {
    private val config = AppConfiguration("test-token", "https://example.org", 1, "Test scooter")
    private fun provider(server: MockWebServer) = ApiClientProvider(config.baseUrl, config.token,
        OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build())
        }.build())

    @Test fun `scooter list uses bearer auth and accepts firmware states`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""[{"id":1,"name":"Test","state":"parked"}]"""))
            val gateway = NavigationRepository { provider(server) }
            assertEquals("parked", gateway.scooters(config).single().state)
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/api/v1/scooters", request.path)
            assertEquals("Bearer test-token", request.getHeader("Authorization"))
        }
    }
    @Test fun `authorization failures expose a useful message`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"code":"forbidden","message":"Location permission required"}}"""))
            try {
                NavigationRepository { provider(server) }.destination(config)
                fail("Forbidden response accepted")
            } catch (error: ClientException) {
                assertEquals(403, error.statusCode)
                assertTrue(userMessage(error).contains("permission"))
            }
        }
    }
    @Test fun `cancelled providers never dispatch a new command`() {
        MockWebServer().use { server ->
            val provider = provider(server)
            provider.cancel()
            assertThrows(java.io.IOException::class.java) { provider.getNavigationApi().clearDestination(1) }
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun `cancelling a repository request cancels its HTTP call`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val provider = provider(server)
            val request = launch(Dispatchers.Default) { NavigationRepository { provider }.destination(config) }
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) })
            request.cancelAndJoin()
            withTimeout(5_000) {
                while (provider.client.dispatcher.runningCallsCount() != 0) delay(10)
            }
            assertTrue(request.isCancelled)
        }
    }
}
