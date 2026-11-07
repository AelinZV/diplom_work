package com.example.greenplanner.integrations.twogis

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale

/**
 * Открывает точку в установленном 2ГИС, а при его отсутствии — в браузере.
 * Документированный формат 2ГИС: https://2gis.ru/geo/{longitude},{latitude}.
 */
fun openPointInTwoGis(context: Context, latitude: Double, longitude: Double): Boolean {
    if (!latitude.isFinite() || !longitude.isFinite() ||
        latitude !in -90.0..90.0 || longitude !in -180.0..180.0
    ) return false

    val coords = String.format(Locale.US, "%.6f,%.6f", longitude, latitude)
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://2gis.ru/geo/$coords"))
        .addCategory(Intent.CATEGORY_BROWSABLE)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
