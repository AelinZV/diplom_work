package com.example.greenplanner.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.greenplanner.data.local.AppDatabase
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Синхронизирует активные напоминания Room с геозонами Google Play Services. */
class GeofenceRegistrar(private val context: Context) {
    private val client = LocationServices.getGeofencingClient(context.applicationContext)

    companion object {
        fun requestId(taskId: Long): String = "task_$taskId"

        fun hasFinePermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        fun hasBackgroundPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else 0
        return PendingIntent.getBroadcast(context, 431, intent, flags)
    }

    @SuppressLint("MissingPermission")
    suspend fun sync(): String {
        if (!hasFinePermission(context)) return "Геозоны неактивны: нужна точная геолокация."
        if (!hasBackgroundPermission(context)) return "Геозоны неактивны: разрешите фоновую геолокацию."

        val targets = AppDatabase.get(context).locationReminderDao().activeTargets()
        if (targets.size > 100) return "Слишком много геозон (${targets.size}). Допускается не более 100."

        return try {
            // Удаляем устаревшие регистрации перед добавлением нового набора.
            client.removeGeofences(pendingIntent()).awaitCompletion()
            if (targets.isEmpty()) return "Активных геонапоминаний нет."

            val geofences = targets.map { target ->
                Geofence.Builder()
                    .setRequestId(requestId(target.taskId))
                    .setCircularRegion(target.latitude, target.longitude, target.radiusMeters.toFloat())
                    .setTransitionTypes(if (target.transition == 2) Geofence.GEOFENCE_TRANSITION_EXIT else Geofence.GEOFENCE_TRANSITION_ENTER)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .build()
            }
            // Без INITIAL_TRIGGER_ENTER: создание напоминания внутри радиуса
            // не приводит к мгновенному уведомлению.
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(0)
                .addGeofences(geofences)
                .build()
            client.addGeofences(request, pendingIntent()).awaitCompletion()
            "Геозоны активны: ${targets.size}. Ожидаем пересечения границы."
        } catch (error: Exception) {
            "Не удалось включить геозоны: ${error.localizedMessage ?: error.javaClass.simpleName}"
        }
    }

    fun removeByTaskId(taskId: Long) {
        client.removeGeofences(listOf(requestId(taskId)))
    }
}

private suspend fun Task<Void>.awaitCompletion(): Unit = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(Unit) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWith(Result.failure(it)) }
}
