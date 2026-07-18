package com.audiophile.musicplayer.audio.visualizer

/**
 * Shared bridge between the playback DSP pipeline and the visualizer.
 * The service sets the spectrum callback; the UI layer registers the analyzer that consumes it.
 */
object VantaAudioAnalyzerHolder {
    @Volatile
    var analyzer: VantaAudioAnalyzer? = null

    fun attach(analyzer: VantaAudioAnalyzer) {
        this.analyzer = analyzer
    }

    fun detach(analyzer: VantaAudioAnalyzer) {
        if (this.analyzer === analyzer) {
            this.analyzer = null
        }
    }
}
