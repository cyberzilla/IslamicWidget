package com.cyberzilla.islamicwidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class UpdateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UpdateReceiver"
        private const val CHANNEL_ID = "update_download_channel"
        private const val NOTIFICATION_ID = 8800
    }

    private val executor = Executors.newSingleThreadExecutor()

    override fun onReceive(context: Context, intent: Intent) {
        val settings = SettingsManager(context)

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

            val fileName = "update_islamicwidget_${settings.latestVersionName}.apk"
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val targetFile = File(downloadDir, fileName)

            // Jika APK sudah terdownload dengan versi yang sama → langsung install
            // Safety net: jika user cancel installer atau notifikasi terhapus,
            // tap lagi di widget langsung memicu install tanpa re-download
            if (targetFile.exists() && targetFile.length() > 100_000) {
                Toast.makeText(context, localizedContext.getString(R.string.update_installing), Toast.LENGTH_SHORT).show()
                installApk(context, localizedContext, fileName)
                return
            }

            Toast.makeText(context, localizedContext.getString(R.string.update_download_start), Toast.LENGTH_SHORT).show()
            startInternalDownload(context, localizedContext, apkUrl, fileName, settings)
        }
    }

    private fun startInternalDownload(
        context: Context,
        localizedContext: Context,
        apkUrl: String,
        fileName: String,
        settings: SettingsManager
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager, localizedContext)

        val appName = localizedContext.getString(R.string.app_name)
        val notifTitle = localizedContext.getString(R.string.update_notification_title, appName)
        val notifDesc = localizedContext.getString(R.string.update_download_desc, settings.latestVersionName)

        // Show initial indeterminate progress notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(notifTitle)
            .setContentText(notifDesc)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        notificationManager.notify(NOTIFICATION_ID, builder.build())

        executor.execute {
            var connection: HttpURLConnection? = null
            var fos: FileOutputStream? = null
            try {
                val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

                // Cleanup old APKs
                downloadDir?.listFiles()?.forEach { file ->
                    if (file.name.startsWith("update_islamicwidget") && file.name.endsWith(".apk") && file.name != fileName) {
                        file.delete()
                    }
                }

                val targetFile = File(downloadDir, fileName)

                val url = URL(apkUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Accept", "application/vnd.android.package-archive")
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Server returned HTTP $responseCode")
                }

                val contentLength = connection.contentLength
                val inputStream = connection.inputStream
                fos = FileOutputStream(targetFile)

                val buffer = ByteArray(8192)
                var totalBytesRead = 0L
                var bytesRead: Int
                var lastNotifyTime = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    // Throttle notification updates to max ~2 per second
                    val now = System.currentTimeMillis()
                    if (contentLength > 0 && now - lastNotifyTime > 500) {
                        lastNotifyTime = now
                        val progress = ((totalBytesRead * 100) / contentLength).toInt()
                        val downloadedMB = String.format(Locale.US, "%.1f", totalBytesRead / 1_048_576.0)
                        val totalMB = String.format(Locale.US, "%.1f", contentLength / 1_048_576.0)

                        builder.setProgress(100, progress, false)
                            .setContentText("$downloadedMB MB / $totalMB MB ($progress%)")
                            .setSmallIcon(android.R.drawable.stat_sys_download)
                        notificationManager.notify(NOTIFICATION_ID, builder.build())
                    }
                }

                fos.flush()
                fos.close()
                fos = null
                inputStream.close()

                // Verify downloaded file
                if (contentLength > 0 && targetFile.length() != contentLength.toLong()) {
                    targetFile.delete()
                    throw Exception("File size mismatch: expected=$contentLength, got=${targetFile.length()}")
                }

                // Show download complete notification with install action
                val installPendingIntent = createInstallPendingIntent(context, fileName)

                builder.setProgress(0, 0, false)
                    .setContentText(localizedContext.getString(R.string.update_download_complete))
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setContentIntent(installPendingIntent)
                notificationManager.notify(NOTIFICATION_ID, builder.build())

                // Auto-trigger install
                installApk(context, localizedContext, fileName)

            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)

                builder.setProgress(0, 0, false)
                    .setContentText(localizedContext.getString(R.string.update_download_failed))
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setOngoing(false)
                    .setAutoCancel(true)
                notificationManager.notify(NOTIFICATION_ID, builder.build())

            } finally {
                try { fos?.close() } catch (_: Exception) {}
                connection?.disconnect()
            }
        }
    }

    private fun createInstallPendingIntent(context: Context, fileName: String): PendingIntent {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return PendingIntent.getActivity(
            context, NOTIFICATION_ID + 1, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

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

    private fun createNotificationChannel(notificationManager: NotificationManager, localizedContext: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                localizedContext.getString(R.string.update_notification_title, localizedContext.getString(R.string.app_name)),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = localizedContext.getString(R.string.update_download_desc, "")
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
