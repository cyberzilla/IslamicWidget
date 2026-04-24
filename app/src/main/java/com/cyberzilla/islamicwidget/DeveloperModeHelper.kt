package com.cyberzilla.islamicwidget

import android.app.AlarmManager
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.MotionEvent
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class DeveloperModeHelper(private val activity: Activity) {

    private val tvHour: TextView = activity.findViewById(R.id.devTvHour)
    private val tvMinute: TextView = activity.findViewById(R.id.devTvMinute)
    private val tvBefore: TextView = activity.findViewById(R.id.devTvBefore)
    private val tvAfter: TextView = activity.findViewById(R.id.devTvAfter)

    private val sliderHour: SeekBar = activity.findViewById(R.id.devSliderHour)
    private val sliderMinute: SeekBar = activity.findViewById(R.id.devSliderMinute)
    private val sliderBefore: SeekBar = activity.findViewById(R.id.devSliderBefore)
    private val sliderAfter: SeekBar = activity.findViewById(R.id.devSliderAfter)

    private val btnTest: Button = activity.findViewById(R.id.devBtnTest)
    private val btnCancel: Button = activity.findViewById(R.id.devBtnCancel)

    // Log area
    private val tvLogContent: TextView = activity.findViewById(R.id.devTvLogContent)
    private val tvLogPath: TextView = activity.findViewById(R.id.devTvLogPath)
    private val btnRefreshLog: Button = activity.findViewById(R.id.devBtnRefreshLog)
    private val btnClearLog: Button = activity.findViewById(R.id.devBtnClearLog)

    fun setup() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, 2)

        sliderHour.progress = cal.get(Calendar.HOUR_OF_DAY)
        sliderMinute.progress = cal.get(Calendar.MINUTE)

        updateTexts()

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { updateTexts() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        sliderHour.setOnSeekBarChangeListener(listener)
        sliderMinute.setOnSeekBarChangeListener(listener)
        sliderBefore.setOnSeekBarChangeListener(listener)
        sliderAfter.setOnSeekBarChangeListener(listener)

        btnTest.setOnClickListener {
            scheduleDeveloperTest(
                sliderHour.progress,
                sliderMinute.progress,
                sliderBefore.progress,
                sliderAfter.progress
            )
        }

        btnCancel.setOnClickListener {
            cancelDeveloperTest()
        }

        // Log area setup
        setupLogViewer()
    }

    private fun setupLogViewer() {
        tvLogPath.text = "📁 ${AdzanLogger.getLogFilePath(activity)}"

        // NestedScrollView menangani scroll — intercept touch agar parent scroll tidak mencuri gesture
        val scrollLog = activity.findViewById<androidx.core.widget.NestedScrollView>(R.id.devScrollLog)
        scrollLog?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        refreshLogContent()

        btnRefreshLog.setOnClickListener {
            refreshLogContent()
            Toast.makeText(activity, "✅ Log di-refresh", Toast.LENGTH_SHORT).show()
        }

        btnClearLog.setOnClickListener {
            AdzanLogger.clearLog(activity)
            refreshLogContent()
            Toast.makeText(activity, "🗑 Log dihapus", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshLogContent() {
        // Coba tampilkan dari file dulu (lebih lengkap), fallback ke memory
        val fileLog = AdzanLogger.readFileLog(activity)
        if (fileLog.isNotEmpty() && !fileLog.startsWith("(")) {
            // Tampilkan baris terakhir di atas (reverse order) agar terbaru terlihat pertama
            val lines = fileLog.trim().lines()
            val reversed = lines.reversed().take(200)
            tvLogContent.text = if (reversed.isEmpty()) "(Belum ada log)" else reversed.joinToString("\n")
        } else {
            // Fallback ke memory log
            val memoryLogs = AdzanLogger.getMemoryLogs()
            if (memoryLogs.isEmpty()) {
                tvLogContent.text = "(Belum ada log)"
            } else {
                tvLogContent.text = memoryLogs.reversed().joinToString("\n")
            }
        }

        // Auto scroll ke atas (terbaru)
        tvLogContent.scrollTo(0, 0)
    }

    private fun updateTexts() {
        tvHour.text = String.format("%02d", sliderHour.progress)
        tvMinute.text = String.format("%02d", sliderMinute.progress)
        tvBefore.text = "${sliderBefore.progress} mnt"
        tvAfter.text = "${sliderAfter.progress} mnt"
    }

    private fun scheduleDeveloperTest(hour: Int, minute: Int, beforeMins: Int, afterMins: Int) {
        val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (targetCal.timeInMillis < System.currentTimeMillis()) {
            targetCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val targetMillis = targetCal.timeInMillis
        val muteMillis = targetMillis - (beforeMins * 60 * 1000L)
        val unmuteMillis = targetMillis + (afterMins * 60 * 1000L)

        val prefs = activity.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("IS_TEST_MODE_ACTIVE", true)
            .putBoolean("PENDING_UNMUTE", false)
            .apply()

        val adzanIntent = Intent(activity, SilentModeReceiver::class.java).apply {
            action = "ACTION_PLAY_ADZAN"
            putExtra("IS_SUBUH", false)
            putExtra("PRAYER_ID", 99)
            putExtra("PRAYER_TIME_MILLIS", targetMillis)
        }
        val adzanPi = PendingIntent.getBroadcast(activity, 9901, adzanIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val muteIntent = Intent(activity, SilentModeReceiver::class.java).apply { action = "ACTION_MUTE" }
        val mutePi = PendingIntent.getBroadcast(activity, 9902, muteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val unmuteIntent = Intent(activity, SilentModeReceiver::class.java).apply { action = "ACTION_UNMUTE" }
        val unmutePi = PendingIntent.getBroadcast(activity, 9903, unmuteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetMillis, adzanPi)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, targetMillis, adzanPi)
        }

        if (beforeMins > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, muteMillis, mutePi)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, muteMillis, mutePi)
            }
        }

        if (afterMins > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, unmuteMillis, unmutePi)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, unmuteMillis, unmutePi)
            }
        }

        // Log test scheduling
        AdzanLogger.log(activity, AdzanLogger.Event.ADZAN_SCHEDULED,
            "DEV TEST dijadwalkan: Adzan=${String.format("%02d:%02d", hour, minute)} | Mute=-${beforeMins}mnt | Unmute=+${afterMins}mnt")
        if (beforeMins > 0) {
            AdzanLogger.logScheduled(activity, 99, muteMillis, "TEST_MUTE")
        }
        AdzanLogger.logScheduled(activity, 99, targetMillis, "TEST_ADZAN")
        if (afterMins > 0) {
            AdzanLogger.logScheduled(activity, 99, unmuteMillis, "TEST_UNMUTE")
        }

        refreshLogContent()

        Toast.makeText(activity, "✅ Tes Dijadwalkan!\nMute: -$beforeMins mnt | Adzan: ${String.format("%02d:%02d", hour, minute)} | Unmute: +$afterMins mnt", Toast.LENGTH_LONG).show()
    }

    private fun cancelDeveloperTest() {
        val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val prefs = activity.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("IS_TEST_MODE_ACTIVE", false)
            .putBoolean("PENDING_UNMUTE", false)
            .apply()

        val adzanIntent = Intent(activity, SilentModeReceiver::class.java).apply { action = "ACTION_PLAY_ADZAN" }
        val muteIntent = Intent(activity, SilentModeReceiver::class.java).apply { action = "ACTION_MUTE" }
        val unmuteIntent = Intent(activity, SilentModeReceiver::class.java).apply { action = "ACTION_UNMUTE" }

        val adzanPi = PendingIntent.getBroadcast(activity, 9901, adzanIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val mutePi = PendingIntent.getBroadcast(activity, 9902, muteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val unmutePi = PendingIntent.getBroadcast(activity, 9903, unmuteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        alarmManager.cancel(adzanPi)
        alarmManager.cancel(mutePi)
        alarmManager.cancel(unmutePi)

        AdzanLogger.log(activity, AdzanLogger.Event.ALARM_CANCELLED, "DEV TEST dibatalkan")
        refreshLogContent()

        Toast.makeText(activity, "❌ Tes Adzan Dibatalkan", Toast.LENGTH_SHORT).show()
    }
}
