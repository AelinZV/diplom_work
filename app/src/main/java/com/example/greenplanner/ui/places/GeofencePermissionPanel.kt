package com.example.greenplanner.ui.places

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.greenplanner.location.GeofenceRegistrar

/** Запрашиваем разрешения поэтапно; Android 11+ выдаёт фоновый доступ через настройки. */
@Composable
fun GeofencePermissionPanel(monitoringMessage: String, onRecheck: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var resumeCount by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeCount++
                onRecheck()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    @Suppress("UNUSED_VARIABLE") val refreshSignal = resumeCount

    val hasFine = GeofenceRegistrar.hasFinePermission(context)
    val hasBackground = GeofenceRegistrar.hasBackgroundPermission(context)
    val hasNotifications = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    val foregroundRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onRecheck()
    }
    val backgroundRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onRecheck()
    }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onRecheck()
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"))
        context.startActivity(intent)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Геонапоминания", style = MaterialTheme.typography.titleMedium)
            Text(monitoringMessage, style = MaterialTheme.typography.bodySmall)
            Text("Точная геолокация: ${if (hasFine) "разрешена" else "не разрешена"}")
            Text("Фоновая геолокация: ${if (hasBackground) "разрешена" else "не разрешена"}")
            Text("Уведомления: ${if (hasNotifications) "разрешены" else "не разрешены"}")
            if (!hasFine) {
                Text("Для геозон необходимо разрешить точное, а не приблизительное местоположение.")
                Button(onClick = {
                    foregroundRequest.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }) { Text("Разрешить точную геолокацию") }
            } else if (!hasBackground) {
                Text("Чтобы напоминания работали с закрытым приложением, разрешите геолокацию «Всегда».")
                Button(onClick = {
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        backgroundRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else openAppSettings()
                }) { Text(if (Build.VERSION.SDK_INT >= 30) "Открыть настройки геолокации" else "Разрешить фоновый доступ") }
            } else {
                Button(onClick = onRecheck) { Text("Обновить геозоны") }
            }
            if (!hasNotifications && Build.VERSION.SDK_INT >= 33) {
                Button(onClick = { notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Разрешить уведомления")
                }
            }
        }
    }
}
