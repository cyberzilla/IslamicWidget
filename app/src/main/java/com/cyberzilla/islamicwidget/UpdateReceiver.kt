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

        // Nama file dinamis menggunakan versionName
        val fileName = "update_islamicwidget_${settings.latestVersionName}.apk"

        if (intent.action == "ACTION_START_UPDATE_DOWNLOAD") {
            val apkUrl = intent.getStringExtra("APK_URL")

            if (apkUrl.isNullOrEmpty() || !apkUrl.endsWith(".apk")) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl ?: "https://github.com/cyberzilla/IslamicWidget"))
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(browserIntent)
                return
            }

            Toast.makeText(context, localizedContext.getString(R.string.update_download_start), Toast.LENGTH_SHORT).show()

            // Opsi tambahan yang baik: Bersihkan file apk lama yang ada di folder Downloads agar tidak menumpuk
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            downloadDir?.listFiles()?.forEach { file ->
                if (file.name.startsWith("update_islamicwidget") && file.name.endsWith(".apk") && file.name != fileName) {
                    file.delete()
                }
            }

            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle(localizedContext.getString(R.string.update_notification_title, localizedContext.getString(R.string.app_name)))
                setDescription(localizedContext.getString(R.string.update_download_desc, settings.latestVersionName))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                // Gunakan nama file yang sudah dinamis
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            settings.latestDownloadId = downloadId
        }
        else if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)

            if (id != -1L && id == settings.latestDownloadId) {
                // Cari menggunakan nama file yang dinamis
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                if (file.exists()) {
                    installApk(context, localizedContext, fileName)
                }
            }
        }
    }

    // Tambahkan parameter fileName agar bisa merujuk ke file yang tepat
    private fun installApk(context: Context, localizedContext: Context, fileName: String) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
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