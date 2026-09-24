package dev.asimonyan.reposcout.data

import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

interface ScoutRepository {
    suspend fun search(query: String, page: Int): SearchPage
    val favorites: Flow<List<Repo>>
    suspend fun toggle(repo: Repo)
}
class DefaultScoutRepository(
    private val api: GitHubApi,
    private val store: ScoutStore,
    private val gson: Gson = Gson(),
    private val clock: () -> Long = System::currentTimeMillis,
) : ScoutRepository {
    override val favorites = store.favorites
    override suspend fun toggle(repo: Repo) = store.toggle(repo)
    override suspend fun search(query: String, page: Int): SearchPage {
        require(query.isNotBlank() && page in 1..50)
        val normalized = query.trim()
        val response = try { api.search(normalized, page) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            val cached = store.cached(normalized, page) ?: throw error
            return SearchPage(gson.fromJson(cached.json, SearchResponse::class.java), cached.savedAt, friendlyError(error))
        }
        // A cache write failure must not discard a successful network response.
        val warning = try {
            store.cache(CachedPage(normalized, page, gson.toJson(response), clock())); null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { "Результат загружен, но не сохранён для просмотра без сети." }
        return SearchPage(response, warning = warning)
    }
}
