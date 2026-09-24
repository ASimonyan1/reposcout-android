package dev.asimonyan.reposcout.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.asimonyan.reposcout.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class SearchState(
    val query: String = "", val items: List<Repo> = emptyList(), val total: Int = 0,
    val page: Int = 0, val loading: Boolean = false, val more: Boolean = false,
    val error: String? = null, val cachedAt: Long? = null, val warning: String? = null,
    val incomplete: Boolean = false, val failedPage: Int = 1,
)
class ScoutViewModel(private val repository: ScoutRepository) : ViewModel() {
    private val mutable = MutableStateFlow(SearchState())
    val state = mutable.asStateFlow()
    val favorites = repository.favorites.catch { messagesChannel.send("Не удалось прочитать избранное."); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val messagesChannel = Channel<String>(Channel.BUFFERED)
    val messages = messagesChannel.receiveAsFlow()
    private var generation = 0L
    private var request: Job? = null

    fun setQuery(text: String) {
        generation++
        request?.cancel()
        mutable.value = SearchState(query = text)
        if (text.isBlank()) return
        mutable.update { it.copy(loading = true) }
        val current = generation
        request = viewModelScope.launch { delay(500); load(text.trim(), 1, current) }
    }
    fun retry() = fetchPage(mutable.value.failedPage)
    fun refresh() = fetchPage(1)
    fun next() { if (mutable.value.more) fetchPage(mutable.value.page + 1) }
    private fun fetchPage(page: Int) {
        val snapshot = mutable.value
        if (snapshot.loading || snapshot.query.isBlank()) return
        val current = ++generation
        request?.cancel()
        mutable.update { it.copy(loading = true, error = null) }
        request = viewModelScope.launch { load(snapshot.query.trim(), page, current) }
    }
    private suspend fun load(query: String, page: Int, current: Long) {
        try {
            val result = repository.search(query, page)
            if (current != generation) return
            mutable.update { previous -> previous.copy(
                items = if (page == 1) result.response.items else mergeUnique(previous.items, result.response.items),
                total = result.response.total, page = page, loading = false, error = null,
                more = hasNext(page, result.response.total, result.response.items.size),
                cachedAt = result.cachedAt ?: if (page == 1) null else previous.cachedAt,
                warning = result.warning, incomplete = result.response.incomplete,
            ) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { if (current == generation) mutable.update { it.copy(loading = false, error = friendlyError(error), failedPage = page) } }
    }
    fun toggle(repo: Repo) = viewModelScope.launch {
        try { repository.toggle(repo) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { messagesChannel.send("Не удалось изменить избранное.") }
    }
}
