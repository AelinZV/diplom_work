package com.example.greenplanner.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.greenplanner.MainActivity

object ReminderNotifier {
    private const val CHANNEL_ID = "greenplanner_location_reminders"

    fun canNotify(context: Context): Boolean {
        val runtimeAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimeAllowed && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun show(context: Context, taskId: Long, taskTitle: String, placeName: String, transition: Int) {
        if (!canNotify(context)) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Напоминания по местоположению", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val openApp = PendingIntent.getActivity(
            context, taskId.toInt(), Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val locationText = if (transition == 2) "Вы покинули место: $placeName" else "Вы рядом с местом: $placeName"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("GreenPlanner: $taskTitle")
            .setContentText(locationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$taskTitle. $locationText"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(taskId.toInt(), notification)
        } catch (_: SecurityException) {
            // Пользователь мог отозвать разрешение непосредственно перед вызовом.
        }
    }
}
