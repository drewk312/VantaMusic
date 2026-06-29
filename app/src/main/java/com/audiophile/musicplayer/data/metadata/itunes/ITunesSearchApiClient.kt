package com.audiophile.musicplayer.data.metadata.itunes

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class ITunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ITunesTrack> = emptyList()
)

data class ITunesTrack(
    @SerializedName("trackId") val trackId: Long = 0,
    @SerializedName("trackName") val trackName: String? = null,
    @SerializedName("artistName") val artistName: String? = null,
    @SerializedName("collectionName") val collectionName: String? = null,
    @SerializedName("collectionArtistName") val collectionArtistName: String? = null,
    @SerializedName("trackTimeMillis") val trackTimeMillis: Long? = null,
    @SerializedName("artworkUrl60") val artworkUrl60: String? = null,
    @SerializedName("artworkUrl100") val artworkUrl100: String? = null,
    @SerializedName("isrc") val isrc: String? = null,
    @SerializedName("primaryGenreName") val primaryGenreName: String? = null,
    @SerializedName("genres") val genres: List<String>? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("trackNumber") val trackNumber: Int? = null,
    @SerializedName("discNumber") val discNumber: Int? = null,
    @SerializedName("trackExplicitness") val trackExplicitness: String? = null,
    @SerializedName("country") val country: String? = null
) {
    fun artworkUrl(size: Int = 600): String? {
        return artworkUrl100?.replace("100x100bb", "${size}x${size}bb")
    }

    val isExplicit: Boolean? get() = when (trackExplicitness) {
        "explicit" -> true
        "notExplicit", "cleaned" -> false
        else -> null
    }
}

class ITunesSearchApiClient {
    private val api: ITunesSearchApi

    init {
        api = Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ITunesSearchApi::class.java)
    }

    suspend fun search(term: String, limit: Int = 25): ITunesSearchResponse {
        return api.search(term = term, entity = "song", limit = limit)
    }

    suspend fun lookupById(id: Long): ITunesSearchResponse {
        return api.lookup(id = id, entity = "song")
    }

    /** Lookup without entity filter — catches IDs that don't surface under entity=song. */
    suspend fun lookupByIdNoEntity(id: Long): ITunesSearchResponse {
        return api.lookupNoEntity(id = id)
    }
}

private interface ITunesSearchApi {
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 25
    ): ITunesSearchResponse

    @GET("lookup")
    suspend fun lookup(
        @Query("id") id: Long,
        @Query("entity") entity: String = "song"
    ): ITunesSearchResponse

    @GET("lookup")
    suspend fun lookupNoEntity(
        @Query("id") id: Long
    ): ITunesSearchResponse
}
