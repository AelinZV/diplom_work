package com.example.greenplanner.integrations.twogis

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/** Результат поиска 2ГИС. Точка хранится как (latitude, longitude) — как в Room. */
data class FoundPlace(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int
)

/**
 * Минимальный клиент 2ГИС Places API без Retrofit.
 * Запрос выполняется на Dispatchers.IO. В production ключ и HTTP-запросы нужно перенести на backend.
 */
class TwoGisPlacesClient(private val apiKey: String) {

    suspend fun search(
        query: String,
        centerLatitude: Double,
        centerLongitude: Double,
        radiusMeters: Int
    ): List<FoundPlace> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Добавьте DGIS_PLACES_KEY в local.properties" }
        require(query.isNotBlank()) { "Введите, что нужно найти" }
        require(centerLatitude in -90.0..90.0 && centerLongitude in -180.0..180.0)
        require(radiusMeters in 100..50000)

        fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
        // В запросах API 2ГИС порядок координат — долгота, широта (lon,lat).
        val center = "$centerLongitude,$centerLatitude"
        val url = URL(
            "https://catalog.api.2gis.com/3.0/items" +
                "?q=${enc(query.trim())}" +
                "&point=${enc(center)}" +
                "&radius=$radiusMeters" +
                "&sort=distance" +
                "&fields=${enc("items.point,items.full_address_name")}" +
                "&page_size=10" +
                "&key=${enc(apiKey)}"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IOException("2ГИС: HTTP $status. Проверьте ключ и его доступ к Places API.")
            }
            val root = JSONObject(responseText)
            val meta = root.optJSONObject("meta")
            if (meta != null && meta.optInt("code", 200) != 200) {
                throw IOException("2ГИС: ошибка API ${meta.optInt("code")}. Проверьте доступ к сервису.")
            }
            val items = root.optJSONObject("result")?.optJSONArray("items")
                ?: return@withContext emptyList()
            buildList {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val point = item.optJSONObject("point") ?: continue
                    val lat = point.optDouble("lat", Double.NaN)
                    val lon = point.optDouble("lon", Double.NaN)
                    if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
                    val out = FloatArray(1)
                    Location.distanceBetween(centerLatitude, centerLongitude, lat, lon, out)
                    add(
                        FoundPlace(
                            id = item.optString("id", "$i"),
                            name = item.optString("name", "Место без названия"),
                            address = item.optString("full_address_name")
                                .ifBlank { item.optString("address_name", "Адрес не указан") },
                            latitude = lat,
                            longitude = lon,
                            distanceMeters = out[0].roundToInt()
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
