package com.example.tiktokunreposter.tiktok

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockTikTokClientTest {
    @Test
    fun mockFlowCanFetchItems() = runBlocking {
        val client = MockTikTokClient(simulateErrors = false)
        assertTrue(client.checkLogin().loggedIn)
        val page = client.fetchRepostedVideos(null)
        assertEquals(30, page.items.size)
        val result = client.removeRepost(page.items.first().videoId)
        assertTrue(result.success)
    }
}
