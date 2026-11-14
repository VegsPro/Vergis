package com.vergis.vegs.util

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Build

object ChatSounds {
    private var lastPlayAt = 0L

    fun playMessage(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastPlayAt < 400) return // debounce
        lastPlayAt = now
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= 28) {
                    ringtone.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                ringtone.play()
                return
            }
        } catch (_: Exception) {}
        // soft fallback tone
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 120)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tg.release() }, 200)
        } catch (_: Exception) {}
    }
}
