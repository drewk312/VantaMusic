package com.audiophile.musicplayer.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackStateHolder {
    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    fun update(transform: NowPlayingState.() -> NowPlayingState) {
        _state.value = _state.value.transform()
    }

    fun replace(newState: NowPlayingState) {
        _state.value = newState
    }

    fun snapshot(): NowPlayingState = _state.value
}
