package com.audiophile.musicplayer.data.dj

import android.util.Log

/**
 * Cheap heuristic to catch LLM-fabricated facts about real artists or tracks.
 * Rejects a narration line when it contains more than one capitalized token
 * that cannot be found anywhere in the track/artist/album metadata we actually
 * passed into the prompt, or common filler words. This isn't a full fact-checker
 * but catches the most obvious hallucinations (invented names, titles, places).
 */
object DjFactGuard {

    private const val TAG = "VANTA_DJ_FACT_GUARD"

    private val FILLER = setOf(
        "the", "of", "and", "up", "next", "now", "here", "here's", "in", "on",
        "for", "a", "an", "one", "two", "this", "that", "yeah", "hey", "baby",
        "right", "time", "set", "round", "again", "from", "to", "we", "it", "is",
        "so", "let's", "back", "tonight", "well", "yeah", "vanta",
    )

    /**
     * Returns `true` when the narration is safe. At most 25 words, and at
     * most one capitalized token is unexplained by the provided metadata.
     */
    fun validate(text: String, metadata: Set<String>): Boolean {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size > 25) {
            Log.d(TAG, "rejected_word_limit words=${words.size} text=${text.take(80)}")
            return false
        }
        val metadataLower = metadata.map { it.lowercase() }
        val unexplained = words.count { raw ->
            val tok = raw.trimEnd(',', '.', '!', '?', ';', ':', '"', '\'', '(', ')', '-', '—')
            tok.isNotEmpty() &&
                tok[0].isUpperCase() &&
                tok.length >= 3 &&
                tok.lowercase() !in FILLER &&
                metadataLower.none { it.contains(tok.lowercase()) || tok.lowercase().contains(it) }
        }
        if (unexplained > 1) {
            Log.d(TAG, "rejected_fabricated_facts unexplained=$unexplained text=${text.take(80)}")
            return false
        }
        return true
    }
}
