package dev.asimonyan.reposcout.data

import androidx.room.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "pages", primaryKeys = ["query", "page"])
data class CachedPage(val query: String, val page: Int, val json: String, val savedAt: Long)
@Entity(tableName = "favorites")
data class Favorite(@PrimaryKey val id: Long, val json: String)

@Dao
interface ScoutDao {
    @Query("SELECT * FROM pages WHERE query = :query AND page = :page")
    suspend fun page(query: String, page: Int): CachedPage?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(page: CachedPage)
    @Query("DELETE FROM pages WHERE rowid NOT IN (SELECT rowid FROM pages ORDER BY savedAt DESC, rowid DESC LIMIT 50)")
    suspend fun prune()
    @Transaction
    suspend fun savePage(page: CachedPage) { put(page); prune() }
    @Query("SELECT * FROM favorites ORDER BY id DESC")
    fun favorites(): Flow<List<Favorite>>
    @Query("SELECT COUNT(*) FROM favorites WHERE id = :id")
    suspend fun favoriteCount(id: Long): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFavorite(item: Favorite)
    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteFavorite(id: Long)
    @Transaction
    suspend fun toggle(item: Favorite) {
        if (favoriteCount(item.id) == 0) putFavorite(item) else deleteFavorite(item.id)
    }
}
@Database(entities = [CachedPage::class, Favorite::class], version = 1, exportSchema = true)
abstract class ScoutDatabase : RoomDatabase() { abstract fun dao(): ScoutDao }

interface ScoutStore {
    suspend fun cached(query: String, page: Int): CachedPage?
    suspend fun cache(page: CachedPage)
    val favorites: Flow<List<Repo>>
    suspend fun toggle(repo: Repo)
}
class RoomScoutStore(private val dao: ScoutDao, private val gson: Gson) : ScoutStore {
    override suspend fun cached(query: String, page: Int) = dao.page(query, page)
    override suspend fun cache(page: CachedPage) = dao.savePage(page)
    override val favorites = dao.favorites().map { list -> list.map { gson.fromJson(it.json, Repo::class.java) } }
    override suspend fun toggle(repo: Repo) = dao.toggle(Favorite(repo.id, gson.toJson(repo)))
}
