package com.audiophile.musicplayer.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.audiophile.musicplayer.appContainer
import com.audiophile.musicplayer.playback.NowPlayingState

open class VantaWidgetBaseProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val state = try {
            (context.applicationContext as? com.audiophile.musicplayer.AudiophileApp)?.appContainer?.playbackStateHolder?.snapshot()
                ?: NowPlayingState()
        } catch (_: Exception) {
            NowPlayingState()
        }
        VantaWidgetUpdater.updateAllWidgets(context, state)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE ||
            intent.action == Intent.ACTION_BOOT_COMPLETED
        ) {
            val state = try {
                (context.applicationContext as? com.audiophile.musicplayer.AudiophileApp)?.appContainer?.playbackStateHolder?.snapshot()
                    ?: NowPlayingState()
            } catch (_: Exception) {
                NowPlayingState()
            }
            VantaWidgetUpdater.updateAllWidgets(context, state)
        }
    }
}

class VantaWidgetExpanded : VantaWidgetBaseProvider()

class VantaWidgetCompact : VantaWidgetBaseProvider()
