package com.audiophile.musicplayer.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ResolverRetrofitFactory {

    fun createCatalogApi(baseUrl: String): CatalogResolverApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CatalogResolverApi::class.java)
    }

    private fun String.ensureTrailingSlash(): String {
        return if (endsWith("/")) this else "$this/"
    }
}
