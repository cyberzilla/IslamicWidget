package com.cyberzilla.islamicwidget

import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.widget.Button
import android.widget.Toast

object AudioAdzanManager {
    private var testMediaPlayer: MediaPlayer? = null
    var isTestingRegular = false
        private set
    var isTestingSubuh = false
        private set

    // FIX A5: Simpan volume alarm asli sebelum diubah agar bisa di-restore
    private var originalAlarmVolume = -1

    fun toggleTestAdzan(
        context: Context,
        isSubuh: Boolean,
        btnPlay: Button,
        tempUri: String?,
        volumePercentage: Int,
        onStopCallback: () -> Unit
    ) {
        if ((isSubuh && isTestingSubuh) || (!isSubuh && isTestingRegular)) {
            stopTestAdzan(context, onStopCallback)
            return
        }
        stopTestAdzan(context, onStopCallback)

        try {
            testMediaPlayer = MediaPlayer().apply {
                setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                }

                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                // FIX A5: Simpan volume asli SEBELUM mengubahnya
                originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val targetVolume = (maxVolume * volumePercentage) / 100
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)

                if (!tempUri.isNullOrEmpty()) {
                    setDataSource(context, Uri.parse(tempUri))
                } else {
                    val afd = context.resources.openRawResourceFd(if (isSubuh) R.raw.adzan_subuh else R.raw.adzan_regular)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }
                prepare()
                start()

                if (isSubuh) isTestingSubuh = true else isTestingRegular = true

                btnPlay.text = context.getString(R.string.btn_stop)
                btnPlay.setTextColor(Color.RED)

                setOnCompletionListener { stopTestAdzan(context, onStopCallback) }
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_audio_error), Toast.LENGTH_SHORT).show()
            stopTestAdzan(context, onStopCallback)
        }
    }

    fun stopTestAdzan(context: Context? = null, onStopCallback: () -> Unit) {
        try {
            testMediaPlayer?.stop()
            testMediaPlayer?.release()
        } catch (e: Exception) {
        } finally {
            testMediaPlayer = null
            isTestingRegular = false
            isTestingSubuh = false

            // FIX A5: Restore volume alarm ke nilai asli setelah test selesai
            if (originalAlarmVolume >= 0 && context != null) {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
                } catch (e: Exception) {}
                originalAlarmVolume = -1
            }

            onStopCallback()
        }
    }
}