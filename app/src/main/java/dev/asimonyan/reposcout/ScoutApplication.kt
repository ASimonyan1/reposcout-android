package dev.asimonyan.reposcout

import android.app.Application
import androidx.room.Room
import com.google.gson.Gson
import dev.asimonyan.reposcout.data.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ScoutApplication : Application() {
    val repository: ScoutRepository by lazy {
        val gson = Gson()
        val db = Room.databaseBuilder(this, ScoutDatabase::class.java, "reposcout.db").build()
        val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
        val api = Retrofit.Builder().baseUrl("https://api.github.com/")
            .client(client).addConverterFactory(GsonConverterFactory.create(gson)).build().create(GitHubApi::class.java)
        DefaultScoutRepository(api, RoomScoutStore(db.dao(), gson), gson)
    }
}
