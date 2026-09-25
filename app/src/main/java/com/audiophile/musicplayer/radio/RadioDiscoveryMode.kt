package com.audiophile.musicplayer.radio

enum class RadioDiscoveryMode(val label: String, val icon: String, val description: String) {
    MY_FAVORITES("Favorites", "♥", "Prioritizes your library, liked tracks, and familiar history"),
    HYBRID_MIX("Hybrid Mix", "⇅", "Balanced 50/50 blend of your top tracks and fresh cloud discoveries"),
    DEEP_DISCOVERY("Deep Discovery", "🌐", "Strictly filters out played tracks to uncover hidden gems and rare B-sides")
}
