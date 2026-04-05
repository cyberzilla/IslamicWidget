package com.cyberzilla.islamicwidget

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = SettingsManager(context)

        // Terapkan lokalisasi bahasa agar toast dan notifikasi sesuai pengaturan aplikasi
        val selectedLocale = Locale.forLanguageTag(settings.languageCode)
        val config = Configuration(context.resources.configuration)
        config.setLocale(selectedLocale)
        val localizedContext = context.createConfigurationContext(config)

        if (intent.action == "ACTION_START_UPDATE_DOWNLOAD") {
            val apkUrl = intent.getStringExtra("APK_URL")

            if (apkUrl.isNullOrEmpty() || !apkUrl.endsWith(".apk")) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl ?: "https://github.com/cyberzilla/IslamicWidget"))
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(browserIntent)
                return
            }

            Toast.makeText(context, localizedContext.getString(R.string.update_download_start), Toast.LENGTH_SHORT).show()

            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle(localizedContext.getString(R.string.update_notification_title, localizedContext.getString(R.string.app_name)))
                setDescription(localizedContext.getString(R.string.update_download_desc, settings.latestVersionName))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "update_islamicwidget.apk")
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            settings.latestDownloadId = downloadId
        }
        else if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)

            if (id != -1L && id == settings.latestDownloadId) {
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update_islamicwidget.apk")
                if (file.exists()) {
                    installApk(context, localizedContext)
                }
            }
        }
    }

    private fun installApk(context: Context, localizedContext: Context) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update_islamicwidget.apk")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, localizedContext.getString(R.string.update_install_failed), Toast.LENGTH_LONG).show()
        }
    }
}