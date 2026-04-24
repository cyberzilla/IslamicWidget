package com.cyberzilla.islamicwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object UpdateHelper {
    private const val METADATA_URL = "https://raw.githubusercontent.com/cyberzilla/IslamicWidget/main/app/release/output-metadata.json"
    private const val DIRECT_APK_URL = "https://github.com/cyberzilla/IslamicWidget/raw/main/app/release/app-release.apk"

    private val executor = Executors.newSingleThreadExecutor()

    fun checkForUpdates(context: Context) {
        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong("LAST_UPDATE_CHECK", 0L)
        if (System.currentTimeMillis() - lastCheck < 6 * 60 * 60 * 1000L) return

        val handler = Handler(Looper.getMainLooper())

        executor.execute {
            try {
                val url = URL(METADATA_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val responseBuilder = StringBuilder()
                    var currentLine: String?

                    while (reader.readLine().also { currentLine = it } != null) {
                        responseBuilder.append(currentLine)
                    }
                    reader.close()

                    val jsonObject = JSONObject(responseBuilder.toString())
                    val elementsArray = jsonObject.getJSONArray("elements")
                    val latestVersionName = elementsArray.getJSONObject(0).getString("versionName")

                    prefs.edit().putLong("LAST_UPDATE_CHECK", System.currentTimeMillis()).apply()

                    handler.post {
                        val settings = SettingsManager(context)

                        val previousKnownVersion = settings.latestVersionName

                        settings.latestVersionName = latestVersionName
                        settings.apkDownloadUrl = DIRECT_APK_URL

                        val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        val currentVersionName = packageInfo.versionName ?: "1.0"

                        if (isVersionNewer(currentVersionName, latestVersionName) && previousKnownVersion != latestVersionName) {
                            val intent = Intent(context, IslamicWidgetProvider::class.java).apply {
                                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                                val widgetManager = AppWidgetManager.getInstance(context)
                                val widgetIds = widgetManager.getAppWidgetIds(ComponentName(context, IslamicWidgetProvider::class.java))
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                            }
                            context.sendBroadcast(intent)
                        }
                    }
                }
                connection.disconnect()
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
        }
    }

    fun isVersionNewer(current: String, latest: String): Boolean {
        val cleanCurrent = current.replace(Regex("[^0-9.]"), "")
        val cleanLatest = latest.replace(Regex("[^0-9.]"), "")

        val currentParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = cleanLatest.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(currentParts.size, latestParts.size)

        for (index in 0 until maxLength) {
            val partCurrent = currentParts.getOrElse(index) { 0 }
            val partLatest = latestParts.getOrElse(index) { 0 }

            if (partLatest > partCurrent) return true
            if (partLatest < partCurrent) return false
        }
        return false
    }
}
