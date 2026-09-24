package dev.asimonyan.reposcout

import dev.asimonyan.reposcout.data.*
import dev.asimonyan.reposcout.ui.ScoutViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ScoutViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private class Fake : ScoutRepository {
        val calls = mutableListOf<Pair<String,Int>>()
        var fail = false
        override val favorites = MutableStateFlow<List<Repo>>(emptyList())
        override suspend fun toggle(repo: Repo) { }
        override suspend fun search(query: String, page: Int): SearchPage {
            calls += query to page
            if (query == "old") withContext(NonCancellable) { delay(1500) }
            if (fail) throw IOException()
            return SearchPage(SearchResponse(total = 60, items = (1..20).map {
                Repo(page * 100L + it, "$query/$it", url = "https://github.com/$query/$it")
            }))
        }
    }
    @Test fun `rapid typing makes only final request`() = runTest(dispatcher) {
        val repository = Fake(); val model = ScoutViewModel(repository)
        model.setQuery("k"); advanceTimeBy(200)
        model.setQuery("kot"); advanceTimeBy(200)
        model.setQuery("kotlin"); advanceUntilIdle()
        assertEquals(listOf("kotlin" to 1), repository.calls)
        assertEquals(20, model.state.value.items.size)
    }
    @Test fun `late uncooperative response cannot replace new search`() = runTest(dispatcher) {
        val model = ScoutViewModel(Fake())
        model.setQuery("old"); advanceTimeBy(501)
        model.setQuery("new"); advanceUntilIdle()
        assertEquals("new", model.state.value.query)
        assertTrue(model.state.value.items.all { it.fullName.startsWith("new/") })
        model.setQuery(""); advanceUntilIdle()
        assertTrue(model.state.value.items.isEmpty())
        assertFalse(model.state.value.loading)
    }
    @Test fun `failed next page preserves items and retries correct page`() = runTest(dispatcher) {
        val repository = Fake(); val model = ScoutViewModel(repository)
        model.setQuery("test"); advanceUntilIdle()
        repository.fail = true; model.next(); advanceUntilIdle()
        assertEquals(20, model.state.value.items.size)
        assertEquals(1, model.state.value.page)
        assertNotNull(model.state.value.error)
        repository.fail = false; model.retry(); advanceUntilIdle()
        assertEquals(40, model.state.value.items.size)
        assertEquals(2, model.state.value.page)
        repository.fail = true; model.refresh(); advanceUntilIdle()
        repository.fail = false; model.retry(); advanceUntilIdle()
        assertEquals("test" to 1, repository.calls.last())
        assertEquals(20, model.state.value.items.size)
    }
}
