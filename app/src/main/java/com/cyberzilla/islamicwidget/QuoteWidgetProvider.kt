package com.cyberzilla.islamicwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.widget.RemoteViews

class QuoteWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_RANDOM_QUOTE = "com.cyberzilla.islamicwidget.ACTION_RANDOM_QUOTE"
        const val ACTION_SHARE_QUOTE = "com.cyberzilla.islamicwidget.ACTION_SHARE_QUOTE"
    }

    private fun dpToPx(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.applicationContext.resources.displayMetrics
        )
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        showShimmer(context, appWidgetManager, appWidgetIds)

        Handler(Looper.getMainLooper()).postDelayed({
            updateAllWidgets(context, appWidgetManager, appWidgetIds)
        }, 500)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, QuoteWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        when (intent.action) {
            ACTION_RANDOM_QUOTE -> {
                showShimmer(context, appWidgetManager, appWidgetIds)

                Handler(Looper.getMainLooper()).postDelayed({
                    updateAllWidgets(context, appWidgetManager, appWidgetIds)

                    val settings = SettingsManager(context)
                    val interval = settings.quoteUpdateInterval
                    if (interval > 0) {
                        QuoteUpdateManager.setAutoUpdate(context, interval)
                    }
                }, 500)
            }
            ACTION_SHARE_QUOTE -> {
                val quote = intent.getStringExtra("EXTRA_QUOTE") ?: return
                val reference = intent.getStringExtra("EXTRA_REFERENCE") ?: return

                val shareText = context.getString(R.string.quote_share_format, quote, reference)

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooserTitle = context.getString(R.string.quote_share_title)
                context.startActivity(Intent.createChooser(shareIntent, chooserTitle).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }

    private fun showShimmer(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quotes)
            // --- INDEX 1 ADALAH SHIMMER SEKARANG ---
            views.setDisplayedChild(R.id.quote_flipper, 1)
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
        }
    }

    private fun updateAllWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return

        val dbHelper = QuoteDatabaseHelper.getInstance(context)
        val settingsManager = SettingsManager(context)
        val quoteData = dbHelper.getRandomQuote()

        val quoteText = quoteData?.first ?: context.getString(R.string.quote_empty_data)
        val quoteRef = quoteData?.second ?: ""

        val fontSize = settingsManager.quoteFontSize.toFloat()
        val refFontSize = (fontSize - 3f).coerceAtLeast(10f)

        val alphaValue = settingsManager.quoteBgAlpha

        // --- LOGIKA ROTASI BARU (Lompati Index 1) ---
        var currentChild = settingsManager.quoteDisplayedChild
        if (currentChild == 1) currentChild = 0

        val nextChild = if (currentChild == 0) 2 else 0
        settingsManager.quoteDisplayedChild = nextChild

        val tvQuoteId = if (nextChild == 0) R.id.tv_quote_text_0 else R.id.tv_quote_text_1
        val tvRefId = if (nextChild == 0) R.id.tv_quote_reference_0 else R.id.tv_quote_reference_1
        // --------------------------------------------

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quotes)

            views.setInt(R.id.quote_bg_image, "setColorFilter", Color.parseColor("#262626"))
            views.setInt(R.id.quote_bg_image, "setImageAlpha", alphaValue)

            views.setTextViewText(tvQuoteId, "\"$quoteText\"")
            views.setTextViewText(tvRefId, quoteRef)

            views.setTextViewTextSize(tvQuoteId, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fontSize))
            views.setTextViewTextSize(tvRefId, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, refFontSize))

            views.setDisplayedChild(R.id.quote_flipper, nextChild)

            views.setContentDescription(R.id.btn_share_quote, context.getString(R.string.quote_desc_share))
            views.setContentDescription(R.id.btn_random_quote, context.getString(R.string.quote_desc_random))

            val randomIntent = Intent(context, QuoteWidgetProvider::class.java).apply {
                action = ACTION_RANDOM_QUOTE
            }
            val randomPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, randomIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_random_quote, randomPendingIntent)

            val shareIntent = Intent(context, QuoteWidgetProvider::class.java).apply {
                action = ACTION_SHARE_QUOTE
                putExtra("EXTRA_QUOTE", quoteText)
                putExtra("EXTRA_REFERENCE", quoteRef)
            }
            val sharePendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, shareIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_share_quote, sharePendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}