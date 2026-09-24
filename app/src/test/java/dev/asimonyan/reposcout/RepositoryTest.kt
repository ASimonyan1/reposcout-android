package dev.asimonyan.reposcout

import com.google.gson.Gson
import dev.asimonyan.reposcout.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class MemoryStore : ScoutStore {
    val pages = mutableMapOf<Pair<String,Int>, CachedPage>()
    var failWrites = false
    override val favorites = MutableStateFlow<List<Repo>>(emptyList())
    override suspend fun cached(query: String, page: Int) = pages[query to page]
    override suspend fun cache(page: CachedPage) {
        if (failWrites) throw IOException("disk full")
        pages[page.query to page.page] = page
    }
    override suspend fun toggle(repo: Repo) {
        favorites.value = if (favorites.value.any { it.id == repo.id }) favorites.value.filterNot { it.id == repo.id } else favorites.value + repo
    }
}
class RepositoryTest {
    private val payload = """{"total_count":1,"incomplete_results":false,"items":[{"id":7,"full_name":"square/retrofit","description":null,"language":"Java","stargazers_count":100,"forks_count":20,"html_url":"https://github.com/square/retrofit"}]}"""
    @Test fun `request is encoded parsed and cached then serves offline`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody(payload).addHeader("Content-Type", "application/json"))
            server.enqueue(MockResponse().setResponseCode(503))
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(GitHubApi::class.java)
            val repository = DefaultScoutRepository(api, MemoryStore(), clock = { 1234L })
            val online = repository.search("  android language:kotlin ", 1)
            assertEquals("square/retrofit", online.response.items.single().fullName)
            assertNull(online.response.items.single().description)
            assertNull(online.cachedAt)
            val request = server.takeRequest()
            assertEquals("android language:kotlin", request.requestUrl!!.queryParameter("q"))
            assertEquals("20", request.requestUrl!!.queryParameter("per_page"))
            assertEquals("1", request.requestUrl!!.queryParameter("page"))
            assertEquals("2022-11-28", request.getHeader("X-GitHub-Api-Version"))
            val offline = repository.search("android language:kotlin", 1)
            assertEquals(online.response, offline.response)
            assertEquals(1234L, offline.cachedAt)
            assertNotNull(offline.warning)
        } finally { server.shutdown() }
    }
    @Test fun `cache keys include both query and page`() = runBlocking {
        val store = MemoryStore()
        store.cache(CachedPage("kotlin", 1, payload, 10))
        val api = object : GitHubApi { override suspend fun search(query: String, page: Int, perPage: Int, sort: String): SearchResponse = throw IOException() }
        val repository = DefaultScoutRepository(api, store)
        assertNotNull(repository.search("kotlin", 1).cachedAt)
        for ((query, page) in listOf("java" to 1, "kotlin" to 2)) {
            try { repository.search(query, page); fail("must not serve unrelated cached results") } catch (_: IOException) { }
        }
    }
    @Test fun `cancellation is never replaced by cache`() = runBlocking {
        val store = MemoryStore()
        store.cache(CachedPage("kotlin", 1, payload, 10))
        val api = object : GitHubApi { override suspend fun search(query: String, page: Int, perPage: Int, sort: String): SearchResponse = throw CancellationException() }
        try { DefaultScoutRepository(api, store).search("kotlin", 1); fail("cancellation swallowed") } catch (_: CancellationException) { }
    }
    @Test fun `disk failure keeps successful response`() = runBlocking {
        val expected = Gson().fromJson(payload, SearchResponse::class.java)
        val api = object : GitHubApi { override suspend fun search(query: String, page: Int, perPage: Int, sort: String) = expected }
        val result = DefaultScoutRepository(api, MemoryStore().apply { failWrites = true }).search("kotlin", 1)
        assertEquals(expected, result.response)
        assertNotNull(result.warning)
        assertNull(result.cachedAt)
    }
    @Test fun `pagination stops at API ceiling and merges duplicate IDs`() {
        assertTrue(hasNext(1, 100, 20))
        assertFalse(hasNext(50, 2000, 20))
        assertFalse(hasNext(1, 20, 20))
        assertFalse(hasNext(1, 100, 3))
        val repo = Repo(1,"a/b",url="https://github.com/a/b")
        assertEquals(listOf(repo), mergeUnique(listOf(repo), listOf(repo)))
    }
}
