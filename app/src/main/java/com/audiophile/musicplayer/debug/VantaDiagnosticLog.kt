package com.audiophile.musicplayer.debug

import android.content.Context
import android.os.Build
import android.util.Log
import com.audiophile.musicplayer.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * On-device ring-buffer + file log for crashes and high-signal errors.
 * Survives process death so Settings can show what broke after a restart.
 */
object VantaDiagnosticLog {
    private const val TAG = "VANTA_DIAG"
    private const val LOG_FILE_NAME = "vanta_diagnostics.log"
    private const val MAX_FILE_BYTES = 512 * 1024
    private const val MAX_MEMORY_LINES = 250

    enum class Level { INFO, WARN, ERROR, CRASH }

    private val memoryLines = CopyOnWriteArrayList<String>()
    private val lock = Any()
    private var appContext: Context? = null
    private var defaultUncaughtHandler: Thread.UncaughtExceptionHandler? = null
    private var installed = false

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun getAppContext(): Context? = appContext

    fun init(context: Context) {
        if (installed) return
        installed = true
        appContext = context.applicationContext
        defaultUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val message = throwable.message ?: ""
            if (message.contains("ACTION_HOVER_EXIT")) {
                Log.w(TAG, "Suppressed known Compose hover bug: $message")
                return@setDefaultUncaughtExceptionHandler
            }
            record(
                level = Level.CRASH,
                tag = "UncaughtException",
                message = "${throwable.javaClass.simpleName}: ${throwable.message ?: "no message"}",
                throwable = throwable,
                threadName = thread.name
            )
            defaultUncaughtHandler?.uncaughtException(thread, throwable)
        }
        record(
            level = Level.INFO,
            tag = "App",
            message = "session_start build=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) " +
                "device=${Build.MANUFACTURER} ${Build.MODEL} api=${Build.VERSION.SDK_INT}"
        )
    }

    fun info(tag: String, message: String) = record(Level.INFO, tag, message)
    fun warn(tag: String, message: String, throwable: Throwable? = null) =
        record(Level.WARN, tag, message, throwable)
    fun error(tag: String, message: String, throwable: Throwable? = null) =
        record(Level.ERROR, tag, message, throwable)

    fun record(
        level: Level,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        threadName: String? = Thread.currentThread().name
    ) {
        val line = buildString {
            append(timeFormat.format(Date()))
            append(' ')
            append(level.name.padEnd(5))
            append(' ')
            append('[').append(tag).append(']')
            if (!threadName.isNullOrBlank()) {
                append(" (").append(threadName).append(')')
            }
            append(' ')
            append(message.trim())
            if (throwable != null) {
                append('\n')
                append(stackTraceString(throwable))
            }
        }
        synchronized(lock) {
            memoryLines.add(line)
            while (memoryLines.size > MAX_MEMORY_LINES) {
                memoryLines.removeAt(0)
            }
        }
        when (level) {
            Level.CRASH, Level.ERROR -> Log.e(TAG, "[$tag] $message", throwable)
            Level.WARN -> Log.w(TAG, "[$tag] $message", throwable)
            Level.INFO -> Log.i(TAG, "[$tag] $message")
        }
        appendToFile(line)
    }

    fun readText(maxLines: Int = 300): String {
        val ctx = appContext ?: return memorySnapshot(maxLines)
        val file = logFile(ctx)
        val fromFile = if (file.exists()) {
            file.readLines().takeLast(maxLines).joinToString("\n")
        } else {
            ""
        }
        if (fromFile.isNotBlank()) return fromFile
        return memorySnapshot(maxLines)
    }

    fun lineCount(): Int {
        val ctx = appContext ?: return memoryLines.size
        val file = logFile(ctx)
        return if (file.exists()) file.readLines().size else memoryLines.size
    }

    fun clear() {
        synchronized(lock) {
            memoryLines.clear()
        }
        appContext?.let { ctx ->
            logFile(ctx).delete()
        }
        info("Diagnostics", "log_cleared_by_user")
    }

    fun logFile(context: Context): File =
        File(context.applicationContext.filesDir, LOG_FILE_NAME)

    private fun memorySnapshot(maxLines: Int): String =
        synchronized(lock) {
            memoryLines.takeLast(maxLines).joinToString("\n")
        }

    private fun appendToFile(line: String) {
        val ctx = appContext ?: return
        try {
            val file = logFile(ctx)
            synchronized(lock) {
                if (file.exists() && file.length() > MAX_FILE_BYTES) {
                    val tail = file.readText().takeLast(MAX_FILE_BYTES / 2)
                    file.writeText("--- log rotated ---\n$tail\n")
                }
                file.appendText(line + "\n")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to append diagnostic log", e)
        }
    }

    private fun stackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return sw.toString().trimEnd()
    }
}
