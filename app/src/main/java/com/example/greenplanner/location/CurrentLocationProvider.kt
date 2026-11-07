package com.example.greenplanner.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken

/**
 * Получает одну актуальную геопозицию по запросу пользователя.
 * Не запускает постоянное отслеживание местоположения.
 */
class CurrentLocationProvider(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    /**
     * Вызывающий обязан проверить ACCESS_COARSE_LOCATION или ACCESS_FINE_LOCATION.
     * Поэтому здесь SuppressLint используется только после проверки разрешений в UI.
     */
    @SuppressLint("MissingPermission")
    fun getCurrentLocation(
        cancellationToken: CancellationToken,
        onResult: (Result<Location>) -> Unit
    ) {
        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken)
                .addOnSuccessListener { location ->
                    if (location == null) {
                        onResult(Result.failure(IllegalStateException("Не удалось определить местоположение. Включите геолокацию и попробуйте ещё раз.")))
                    } else {
                        onResult(Result.success(location))
                    }
                }
                .addOnFailureListener { exception ->
                    onResult(Result.failure(exception))
                }
        } catch (exception: SecurityException) {
            onResult(Result.failure(exception))
        } catch (exception: Exception) {
            onResult(Result.failure(exception))
        }
    }
}
