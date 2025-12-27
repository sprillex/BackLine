package com.example.offlinebrowser.data.network

import retrofit2.http.GET
import retrofit2.http.Query

interface KiwixService {
    @GET("catalog/v2/entries")
    suspend fun search(@Query("q") query: String): String
}
