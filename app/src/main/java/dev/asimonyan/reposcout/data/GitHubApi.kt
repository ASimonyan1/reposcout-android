package dev.asimonyan.reposcout.data

import retrofit2.HttpException
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import java.io.IOException

interface GitHubApi {
    @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28", "User-Agent: RepoScout-Android")
    @GET("search/repositories")
    suspend fun search(@Query("q") query: String, @Query("page") page: Int,
        @Query("per_page") perPage: Int = PAGE_SIZE, @Query("sort") sort: String = "stars"): SearchResponse
}

fun friendlyError(error: Throwable): String = when (error) {
    is IOException -> "Нет связи с GitHub. Проверьте интернет и повторите запрос."
    is HttpException -> when (error.code()) {
        403, 429 -> "GitHub ограничил запросы. Подождите немного и повторите попытку."
        422 -> "GitHub не принял запрос. Проверьте поисковую строку."
        in 500..599 -> "GitHub временно недоступен. Попробуйте позже."
        else -> "Ошибка GitHub: HTTP ${error.code()}."
    }
    else -> "Не удалось загрузить данные. Попробуйте ещё раз."
}
