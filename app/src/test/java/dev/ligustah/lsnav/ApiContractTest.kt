package dev.ligustah.lsnav

import dev.ligustah.lsnav.api.generated.models.Destination
import dev.ligustah.lsnav.api.generated.models.DestinationInput
import dev.ligustah.lsnav.api.generated.models.Scooter
import org.junit.Assert.*
import org.junit.Test
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.infrastructure.Serializer

class ApiContractTest {
    @Test fun `scooter decoder accepts current and future firmware states`() {
        for (state in listOf("parked", "ready-to-drive", "unknown", "future-state")) {
            val json = """{"id":1,"name":"Scooter","vin":null,"color":null,"state":"$state","blinkers":"future-state"}"""
            val scooter = Serializer.moshi.adapter(Scooter::class.java).fromJson(json)!!
            assertEquals(state, scooter.state)
            assertEquals("Scooter", scooter.name)
        }
    }
    @Test fun `destination allows null coordinates but input requires them`() {
        assertNull(Serializer.moshi.adapter(Destination::class.java).fromJson("""{"latitude":null,"longitude":null,"address":null}""")!!.latitude)
        assertThrows(com.squareup.moshi.JsonDataException::class.java) {
            Serializer.moshi.adapter(DestinationInput::class.java).fromJson("""{"latitude":null,"longitude":null}""")
        }
    }
    @Test fun `server URL normalization is idempotent`() {
        assertEquals("https://example.org/api/v1", ApiClientProvider.normalizeBaseUrl(" https://example.org/ "))
        assertEquals("https://example.org/prefix/api/v1", ApiClientProvider.normalizeBaseUrl("https://example.org/prefix/api/v1/"))
    }
    @Test fun `unsafe server URLs are rejected`() {
        for (url in listOf("http://example.org", "not a url", "https://user:secret@example.org", "https://example.org/?x=1", "https://example.org/#fragment")) {
            assertThrows(IllegalArgumentException::class.java) { ApiClientProvider.normalizeBaseUrl(url) }
        }
    }
    @Test fun `HTTP errors have actionable messages`() {
        assertTrue(userMessage(ClientException(statusCode = 401)).contains("token"))
        assertTrue(userMessage(ClientException(statusCode = 403)).contains("permission"))
        assertTrue(userMessage(ClientException(statusCode = 404)).contains("select"))
        assertTrue(userMessage(ClientException(statusCode = 422)).contains("connectivity"))
    }
}
