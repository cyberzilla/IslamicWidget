package com.cyberzilla.islamicwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Qibla
import java.util.Locale

class CompassWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_compass)

        // Mengambil lokasi tersimpan untuk menghitung derajat Kiblat
        val settings = SettingsManager(context)
        val latStr = settings.latitude
        val lonStr = settings.longitude
        var degreeText = "--°"

        if (latStr != null && lonStr != null) {
            try {
                val coordinates = Coordinates(latStr.toDouble(), lonStr.toDouble())
                val qiblaDegree = Qibla(coordinates).direction.toFloat()
                // Format hanya angkanya saja agar muat di ukuran 1x1
                degreeText = String.format(Locale.getDefault(), "%.1f°", qiblaDegree)
            } catch (e: Exception) {
                degreeText = "Err"
            }
        }

        views.setTextViewText(R.id.tv_widget_qibla_degree, degreeText)

        val intent = Intent(context, CompassActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.widget_compass_root, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}