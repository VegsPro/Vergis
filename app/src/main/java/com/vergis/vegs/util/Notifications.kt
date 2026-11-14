package com.vergis.vegs.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vergis.vegs.MainActivity
import com.vergis.vegs.R

object Notifications {
    private const val CHANNEL_ID = "vergis_game"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Игра Vergis",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Ответы игроков и события комнаты" }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    fun showAnswer(context: Context, playerName: String, answered: Int, total: Int, roomId: String) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("roomId", roomId)
        }
        val pi = PendingIntent.getActivity(
            context, roomId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (answered >= total) {
            "Все ответили · $answered/$total"
        } else {
            "$playerName ответил(а) · $answered/$total"
        }
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Vergis")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(roomId.hashCode() and 0x7FFF, notif)
        } catch (_: SecurityException) {}
    }

    fun showInvite(context: Context, fromName: String, roomName: String, roomId: String) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("roomId", roomId)
            putExtra("invite", true)
        }
        val pi = PendingIntent.getActivity(
            context, (roomId + "inv").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Приглашение в комнату")
            .setContentText("$fromName зовёт в «$roomName»")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(("inv" + roomId).hashCode() and 0x7FFF, notif)
        } catch (_: SecurityException) {}
    }

    fun showPlayerLeft(context: Context, name: String, reason: String) {
        ensureChannel(context)
        val text = when (reason) {
            "afk" -> "$name отошёл (AFK) и покинул комнату"
            else -> "$name вышел из комнаты"
        }
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Vergis")
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(name.hashCode() and 0x7FFF, notif)
        } catch (_: SecurityException) {}
    }
}

