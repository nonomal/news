package org.vestifeed.backend

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.vestifeed.auth.AuthEvents
import org.vestifeed.db.Database

class MinifluxUnauthenticatedTest {

    private lateinit var server: MockWebServer
    private lateinit var db: Database

    @Before
    fun before() {
        server = MockWebServer().apply { start() }
        db = Database(BundledSQLiteDriver(), ":memory:")
        AuthEvents.reset()
    }

    @After
    fun after() {
        server.shutdown()
        AuthEvents.reset()
    }

    @Test
    fun unauthorizedResponseThrowsAndSignalsEvent() {
        server.enqueue(MockResponse().setResponseCode(401))

        val api = Miniflux(
            client = minifluxHttpClient(token = "expired"),
            baseUrl = server.url("/v1/"),
            db = db,
        )

        val countBefore = AuthEvents.invalidationCount.value
        assertThrows(MinifluxUnauthenticatedException::class.java) {
            runBlocking { api.getFeeds() }
        }
        assertEquals(countBefore + 1, AuthEvents.invalidationCount.value)
    }

    @Test
    fun otherErrorCodesDoNotSignalAuthEvent() {
        server.enqueue(MockResponse().setResponseCode(500))

        val api = Miniflux(
            client = minifluxHttpClient(token = "expired"),
            baseUrl = server.url("/v1/"),
            db = db,
        )

        val countBefore = AuthEvents.invalidationCount.value
        assertThrows(java.io.IOException::class.java) {
            runBlocking { api.getFeeds() }
        }
        assertEquals(countBefore, AuthEvents.invalidationCount.value)
    }
}
