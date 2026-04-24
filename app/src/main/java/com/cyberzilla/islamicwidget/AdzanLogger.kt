package com.cyberzilla.islamicwidget

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sistem logging untuk mendiagnosa event adzan, auto-silent, dan scheduling.
 * Log disimpan ke file yang bisa dibaca via file manager:
 *   Documents/IslamicWidget/adzan_log.txt
 *
 * Setiap entry berisi timestamp, event type, dan detail.
 */
object AdzanLogger {

    private const val TAG = "AdzanLogger"
    private const val LOG_DIR_NAME = "IslamicWidget"
    private const val LOG_FILE_NAME = "adzan_log.txt"
    private const val MAX_LOG_LINES = 1500
    private const val MAX_MEMORY_ENTRIES = 200

    // In-memory circular log untuk ditampilkan di developer area
    private val memoryLog = mutableListOf<String>()
    private val lock = Any()

    enum class Event {
        // Scheduling
        ALARM_SCHEDULED,
        ALARM_CANCELLED,
        ALARM_RESCHEDULED,

        // Auto Silent
        MUTE_EXECUTED,
        MUTE_SKIPPED,
        UNMUTE_EXECUTED,
        UNMUTE_DEFERRED,
        UNMUTE_PENDING,

        // Adzan
        ADZAN_SCHEDULED,
        ADZAN_FIRED,
        ADZAN_PLAY_START,
        ADZAN_COMPLETED,
        ADZAN_INTERRUPTED,
        ADZAN_ERROR,
        ADZAN_SKIPPED,

        // System
        WIDGET_UPDATE,
        BOOT_RESCHEDULE,
        AUDIO_FOCUS_CHANGE,
    }

    // Background executor untuk file I/O agar tidak blocking main thread
    private val fileExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var writeCount = 0

    /**
     * Log sebuah event. Otomatis menyimpan ke file dan memory.
     *
     * @param context Context Android
     * @param event Jenis event
     * @param detail Informasi tambahan
     */
    fun log(context: Context, event: Event, detail: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = "[$timestamp] ${event.name}: $detail"

        Log.d(TAG, entry)

        synchronized(lock) {
            memoryLog.add(entry)
            if (memoryLog.size > MAX_MEMORY_ENTRIES) {
                memoryLog.removeAt(0)
            }
        }

        // Simpan ke file secara ASYNC agar tidak blocking main thread / BroadcastReceiver
        val appContext = context.applicationContext
        fileExecutor.execute {
            try {
                val logFile = getLogFile(appContext)
                if (logFile != null) {
                    logFile.parentFile?.mkdirs()
                    logFile.appendText("$entry\n")

                    // Trim hanya setiap 50 tulis, bukan setiap kali (hemat I/O)
                    writeCount++
                    if (writeCount % 50 == 0) {
                        trimLogFile(logFile)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menulis log ke file", e)
            }
        }
    }

    /**
     * Ambil semua log dari memory untuk ditampilkan di UI developer.
     */
    fun getMemoryLogs(): List<String> {
        synchronized(lock) {
            return memoryLog.toList()
        }
    }

    /**
     * Baca log dari file (untuk display yang lebih lengkap).
     */
    fun readFileLog(context: Context): String {
        return try {
            val logFile = getLogFile(context)
            if (logFile != null && logFile.exists()) {
                logFile.readText()
            } else {
                "(Log file belum dibuat)"
            }
        } catch (e: Exception) {
            "(Error membaca log: ${e.message})"
        }
    }

    /**
     * Hapus semua log.
     */
    fun clearLog(context: Context) {
        synchronized(lock) {
            memoryLog.clear()
        }
        try {
            val logFile = getLogFile(context)
            logFile?.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menghapus log", e)
        }
    }

    /**
     * Dapatkan path file log.
     */
    fun getLogFilePath(context: Context): String {
        return getLogFile(context)?.absolutePath ?: "(tidak tersedia)"
    }

    private fun getLogFile(context: Context): File? {
        return try {
            // Prioritas 1: Android/media/<package>/ (bisa diakses via file manager tanpa root)
            val mediaDir = File(
                android.os.Environment.getExternalStorageDirectory(),
                "Android/media/${context.packageName}"
            )
            mediaDir.mkdirs()
            File(mediaDir, LOG_FILE_NAME)
        } catch (e: Exception) {
            try {
                // Fallback: internal app files
                File(context.filesDir, "$LOG_DIR_NAME/$LOG_FILE_NAME")
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun trimLogFile(file: File) {
        try {
            if (!file.exists()) return
            val lines = file.readLines()
            if (lines.size > MAX_LOG_LINES) {
                val trimmed = lines.takeLast(MAX_LOG_LINES)
                file.writeText(trimmed.joinToString("\n") + "\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal trim log file", e)
        }
    }

    // ================== Helper methods untuk log yang lebih readable ==================

    fun logScheduled(context: Context, prayerId: Int, triggerTimeMs: Long, type: String) {
        val prayerName = getPrayerName(prayerId)
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(triggerTimeMs))
        log(context, Event.ALARM_SCHEDULED, "$type untuk $prayerName dijadwalkan pada $timeStr")
    }

    fun logAdzanFired(context: Context, prayerId: Int) {
        val prayerName = getPrayerName(prayerId)
        log(context, Event.ADZAN_FIRED, "Receiver menerima ACTION_PLAY_ADZAN untuk $prayerName")
    }

    fun logAdzanPlayStart(context: Context, prayerId: Int, isSubuh: Boolean) {
        val prayerName = getPrayerName(prayerId)
        val type = if (isSubuh) "Subuh" else "Regular"
        log(context, Event.ADZAN_PLAY_START, "MediaPlayer mulai memutar adzan $type untuk $prayerName")
    }

    fun logAdzanCompleted(context: Context, prayerId: Int) {
        val prayerName = getPrayerName(prayerId)
        log(context, Event.ADZAN_COMPLETED, "Adzan $prayerName selesai diputar sampai akhir (natural completion)")
    }

    fun logAdzanInterrupted(context: Context, prayerId: Int, reason: String) {
        val prayerName = getPrayerName(prayerId)
        log(context, Event.ADZAN_INTERRUPTED, "Adzan $prayerName dihentikan: $reason")
    }

    fun logMuteExecuted(context: Context, method: String) {
        log(context, Event.MUTE_EXECUTED, "Perangkat di-mute via $method")
    }

    fun logUnmuteExecuted(context: Context, method: String) {
        log(context, Event.UNMUTE_EXECUTED, "Perangkat di-unmute via $method")
    }

    private fun getPrayerName(prayerId: Int): String {
        return when (prayerId) {
            1 -> "Subuh"
            2 -> "Dzuhur/Jumat"
            3 -> "Ashar"
            4 -> "Maghrib"
            5 -> "Isya"
            99 -> "TEST"
            else -> "Unknown($prayerId)"
        }
    }
}
