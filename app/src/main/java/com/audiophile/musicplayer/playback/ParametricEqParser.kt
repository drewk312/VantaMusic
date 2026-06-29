package com.audiophile.musicplayer.playback

data class EqBand(
    val filterNumber: Int,
    val type: String, // e.g. "PK" (Peaking)
    val frequency: Float, // Fc in Hz
    val gain: Float, // Gain in dB
    val qValue: Float // Q
)

data class EqPreset(
    val preamp: Float, // Preamp in dB
    val bands: List<EqBand>
)

object ParametricEqParser {

    /**
     * Parses a standard Squig.link / AutoEq parametric EQ txt file.
     * Example lines:
     * Preamp: -3.0 dB
     * Filter 1: ON PK Fc 21 Hz Gain -0.9 dB Q 1.800
     */
    fun parsePreset(fileContent: String): EqPreset {
        var preamp = 0f
        val bands = mutableListOf<EqBand>()

        val preampRegex = Regex("Preamp:\\s*([\\-\\d\\.]+)\\s*dB", RegexOption.IGNORE_CASE)
        val filterRegex = Regex("Filter\\s+(\\d+):\\s*ON\\s+(\\w+)\\s+Fc\\s+([\\d\\.]+)\\s+Hz\\s+Gain\\s+([\\-\\d\\.]+)\\s+dB\\s+Q\\s+([\\d\\.]+)", RegexOption.IGNORE_CASE)

        fileContent.lines().forEach { line ->
            preampRegex.find(line)?.let { match ->
                preamp = match.groupValues[1].toFloatOrNull() ?: 0f
            }

            filterRegex.find(line)?.let { match ->
                bands.add(
                    EqBand(
                        filterNumber = match.groupValues[1].toInt(),
                        type = match.groupValues[2],
                        frequency = match.groupValues[3].toFloat(),
                        gain = match.groupValues[4].toFloat(),
                        qValue = match.groupValues[5].toFloat()
                    )
                )
            }
        }

        return EqPreset(preamp, bands)
    }
}
