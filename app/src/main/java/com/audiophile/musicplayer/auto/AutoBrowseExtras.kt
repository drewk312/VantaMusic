package com.audiophile.musicplayer.auto

import android.net.Uri
import android.os.Bundle

object AutoBrowseExtras {
    private const val SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
    private const val CONTENT_STYLE_BROWSABLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
    private const val CONTENT_STYLE_PLAYABLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
    private const val CONTENT_STYLE_SINGLE_ITEM = "android.media.browse.CONTENT_STYLE_SINGLE_ITEM_HINT"
    private const val CONTENT_STYLE_GROUP_TITLE = "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"
    private const val CONTENT_STYLE_LIST_ITEM = 1
    private const val CONTENT_STYLE_GRID_ITEM = 2

    private const val EXTRA_ARTWORK_URI = "vanta_artwork_uri"
    private const val EXTRA_DURATION_MS = "vanta_duration_ms"
    private const val EXTRA_IS_FAVORITE = "vanta_is_favorite"
    private const val EXTRA_GROUP_TITLE = "vanta_group_title"

    fun rootExtras(): Bundle = Bundle().apply {
        putBoolean(SEARCH_SUPPORTED, true)
        putInt(CONTENT_STYLE_BROWSABLE, CONTENT_STYLE_LIST_ITEM)
        putInt(CONTENT_STYLE_PLAYABLE, CONTENT_STYLE_LIST_ITEM)
    }

    fun listItemExtras(): Bundle = Bundle().apply {
        putInt(CONTENT_STYLE_BROWSABLE, CONTENT_STYLE_LIST_ITEM)
        putInt(CONTENT_STYLE_PLAYABLE, CONTENT_STYLE_LIST_ITEM)
        putInt(CONTENT_STYLE_SINGLE_ITEM, CONTENT_STYLE_LIST_ITEM)
    }

    fun listItemExtras(
        artworkUrl: String?,
        durationMs: Long,
        isFavorite: Boolean = false
    ): Bundle = Bundle().apply {
        putInt(CONTENT_STYLE_BROWSABLE, CONTENT_STYLE_LIST_ITEM)
        putInt(CONTENT_STYLE_PLAYABLE, CONTENT_STYLE_LIST_ITEM)
        putInt(CONTENT_STYLE_SINGLE_ITEM, CONTENT_STYLE_LIST_ITEM)
        if (!artworkUrl.isNullOrBlank()) {
            putString(EXTRA_ARTWORK_URI, artworkUrl)
        }
        if (durationMs > 0) putLong(EXTRA_DURATION_MS, durationMs)
        putBoolean(EXTRA_IS_FAVORITE, isFavorite)
    }

    fun gridItemExtras(): Bundle = Bundle().apply {
        putInt(CONTENT_STYLE_BROWSABLE, CONTENT_STYLE_GRID_ITEM)
        putInt(CONTENT_STYLE_PLAYABLE, CONTENT_STYLE_GRID_ITEM)
        putInt(CONTENT_STYLE_SINGLE_ITEM, CONTENT_STYLE_GRID_ITEM)
    }

    fun gridItemExtras(artworkUrl: String?): Bundle = Bundle().apply {
        putInt(CONTENT_STYLE_BROWSABLE, CONTENT_STYLE_GRID_ITEM)
        putInt(CONTENT_STYLE_PLAYABLE, CONTENT_STYLE_GRID_ITEM)
        putInt(CONTENT_STYLE_SINGLE_ITEM, CONTENT_STYLE_GRID_ITEM)
        if (!artworkUrl.isNullOrBlank()) {
            putString(EXTRA_ARTWORK_URI, artworkUrl)
        }
    }

    fun groupTitleExtras(title: String): Bundle = Bundle().apply {
        putString(CONTENT_STYLE_GROUP_TITLE, title)
        putInt(CONTENT_STYLE_SINGLE_ITEM, CONTENT_STYLE_LIST_ITEM)
    }

    fun artworkUri(extras: Bundle?): String? = extras?.getString(EXTRA_ARTWORK_URI)
    fun durationMs(extras: Bundle?): Long = extras?.getLong(EXTRA_DURATION_MS, 0L) ?: 0L
    fun isFavorite(extras: Bundle?): Boolean = extras?.getBoolean(EXTRA_IS_FAVORITE, false) ?: false
}
