package com.audiophile.musicplayer.playback.dsp

import android.content.SharedPreferences
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
class ParametricEqProcessorSafetyTest {

    @Test
    fun queueInput_withoutConfigure_bypassesOnFailureWithoutLeavingInputUnconsumed() {
        val processor = ParametricEqProcessor(InMemorySharedPreferences())
        processor.setEnabled(true)
        val input = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        input.putShort(1000)
        input.putShort(-1000)
        input.flip()

        processor.queueInput(input)

        assertTrue(input.position() == input.limit())
    }

    @Test
    fun queueInput_whenDisabled_passesThroughWithoutMuting() {
        val processor = ParametricEqProcessor(InMemorySharedPreferences())
        processor.setEnabled(false)
        val input = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        input.putShort(1000)
        input.putShort(-1000)
        input.flip()

        processor.queueInput(input)

        assertTrue(input.position() == input.limit())
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = key in data
        override fun edit(): SharedPreferences.Editor = Editor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private class Editor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {
            override fun putString(key: String, value: String?) = apply { data[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { data[key] = values }
            override fun putInt(key: String, value: Int) = apply { data[key] = value }
            override fun putLong(key: String, value: Long) = apply { data[key] = value }
            override fun putFloat(key: String, value: Float) = apply { data[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { data[key] = value }
            override fun remove(key: String) = apply { data.remove(key) }
            override fun clear() = apply { data.clear() }
            override fun commit(): Boolean = true
            override fun apply() = Unit
        }
    }
}
*/
