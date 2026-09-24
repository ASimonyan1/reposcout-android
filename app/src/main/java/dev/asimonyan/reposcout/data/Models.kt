package dev.asimonyan.reposcout.data

import com.google.gson.annotations.SerializedName

data class Repo(
    val id: Long,
    @SerializedName("full_name") val fullName: String,
    val description: String? = null,
    val language: String? = null,
    @SerializedName("stargazers_count") val stars: Int = 0,
    @SerializedName("forks_count") val forks: Int = 0,
    @SerializedName("html_url") val url: String,
)
data class SearchResponse(
    @SerializedName("total_count") val total: Int = 0,
    @SerializedName("incomplete_results") val incomplete: Boolean = false,
    val items: List<Repo> = emptyList(),
)
data class SearchPage(val response: SearchResponse, val cachedAt: Long? = null, val warning: String? = null)
const val PAGE_SIZE = 20
fun hasNext(page: Int, total: Int, received: Int): Boolean =
    received == PAGE_SIZE && page * PAGE_SIZE < minOf(total, 1000)
fun mergeUnique(previous: List<Repo>, next: List<Repo>): List<Repo> = (previous + next).distinctBy { it.id }
