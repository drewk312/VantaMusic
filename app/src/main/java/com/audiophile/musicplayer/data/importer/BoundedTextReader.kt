package com.audiophile.musicplayer.data.importer

import java.io.Reader

const val MAX_IMPORT_TEXT_CHARS: Int = 2_000_000

/** Reads at most [maxChars], returning null when the input exceeds the limit. */
fun Reader.readBoundedText(maxChars: Int = MAX_IMPORT_TEXT_CHARS): String? {
    require(maxChars > 0)
    val output = StringBuilder(minOf(maxChars, 16_384))
    val buffer = CharArray(8_192)
    var total = 0
    while (true) {
        val remainingWithOverflowSentinel = maxChars - total + 1
        val count = read(buffer, 0, minOf(buffer.size, remainingWithOverflowSentinel))
        if (count < 0) return output.toString()
        total += count
        if (total > maxChars) return null
        output.append(buffer, 0, count)
    }
}
