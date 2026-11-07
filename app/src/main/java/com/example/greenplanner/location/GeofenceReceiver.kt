package com.example.greenplanner.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.greenplanner.data.local.AppDatabase
import com.example.greenplanner.notifications.ReminderNotifier
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Получает ENTER/EXIT даже при закрытом интерфейсе приложения. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w("GreenPlannerGeofence", "Ошибка геозоны: ${event.errorCode}")
            return
        }
        val transition = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> 1
            Geofence.GEOFENCE_TRANSITION_EXIT -> 2
            else -> return
        }
        val requestIds = event.triggeringGeofences.orEmpty().mapNotNull { fence ->
            fence.requestId.removePrefix("task_").toLongOrNull()
                ?.takeIf { fence.requestId == GeofenceRegistrar.requestId(it) }
        }
        if (requestIds.isEmpty()) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(appContext).locationReminderDao()
                val registrar = GeofenceRegistrar(appContext)
                for (taskId in requestIds) {
                    // Если уведомления выключены, не помечаем напоминание как сработавшее.
                    if (!ReminderNotifier.canNotify(appContext)) continue
                    if (dao.markFiredIfActive(taskId, transition) == 1) {
                        val data = dao.notificationData(taskId)
                        if (data != null) {
                            ReminderNotifier.show(appContext, taskId, data.title, data.placeName, transition)
                        }
                        registrar.removeByTaskId(taskId)
                    }
                }
            } catch (error: Exception) {
                Log.e("GreenPlannerGeofence", "Ошибка обработки события", error)
            } finally {
                pending.finish()
            }
        }
    }
}
