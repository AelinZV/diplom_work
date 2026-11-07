package com.example.greenplanner.integrations.twogis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Найденное здание или адрес. В приложении порядок координат: широта, долгота. */
data class GeocodedAddress(
    val id: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Геокодирование через 2ГИС. 404 с itemNotFound означает отсутствие совпадений,
 * а не обязательно недействительный API-ключ.
 *
 * nearLatitude / nearLongitude — необязательная подсказка, около какого города
 * искать короткий адрес вроде «Народная, 81».
 */
class TwoGisGeocoder(private val apiKey: String) {
    suspend fun findAddress(
        query: String,
        nearLatitude: Double? = null,
        nearLongitude: Double? = null
    ): List<GeocodedAddress> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Не настроен ключ DGIS_PLACES_KEY" }
        val cleaned = query.trim()
        require(cleaned.length >= 4) { "Введите улицу и номер дома" }

        val location = if (
            nearLatitude != null && nearLongitude != null &&
            nearLatitude.isFinite() && nearLongitude.isFinite() &&
            nearLatitude in -85.0..85.0 && nearLongitude in -180.0..180.0
        ) {
            String.format(Locale.US, "&location=%.7f,%.7f", nearLongitude, nearLatitude)
        } else ""

        val fields = "items.point,items.geometry.centroid,items.full_address_name"
        val common = "?q=${encode(cleaned)}&fields=${encode(fields)}&page_size=7" +
            "&key=${encode(apiKey)}"
        val geocoderUrl = "https://catalog.api.2gis.com/3.0/items/geocode" + common +
            "&type=building" + location
        val direct = request(geocoderUrl, "Geocoder API")
        val matches = parseAddresses(direct)
        if (matches.isNotEmpty()) return@withContext matches

        // Places API умеет искать здания по адресу и уже используется
        // для поиска организаций в этом приложении.
        val placesUrl = "https://catalog.api.2gis.com/3.0/items" + common +
            "&type=building" + if (location.isNotBlank()) {
                "&point=" + location.removePrefix("&location=") + "&radius=20000"
            } else ""
        parseAddresses(request(placesUrl, "Places API"))
    }

    /**
     * Определяет адрес ближайшего здания не дальше 20 м от выбранной точки.
     * Один вызов = один запрос Geocoder API. Если здание не найдено,
     * возвращает null, не подставляя адрес соседнего района или улицы.
     *
     * Вызывается только после касания карты / завершения перетаскивания,
     * а не при загрузке картинки карты или изменении масштаба.
     */
    suspend fun reverseAddress(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "Не настроен ключ DGIS_PLACES_KEY" }
            require(latitude.isFinite() && latitude in -90.0..90.0 &&
                longitude.isFinite() && longitude in -180.0..180.0) {
                "Недопустимые координаты"
            }

            val url = "https://catalog.api.2gis.com/3.0/items/geocode" +
                "?lon=${String.format(Locale.US, "%.7f", longitude)}" +
                "&lat=${String.format(Locale.US, "%.7f", latitude)}" +
                "&type=building&radius=20&page_size=1" +
                "&fields=${encode("items.adm_div,items.address")}" +
                "&key=${encode(apiKey)}"

            val items = request(url, "Geocoder API")
                ?.optJSONObject("result")?.optJSONArray("items")
                ?: return@withContext null
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                if (item.optString("type") != "building") continue
                // Только адрес здания, не район/административная единица.
                val address = listOf(
                    item.optString("full_name"),
                    item.optString("full_address_name"),
                    item.optString("address_name")
                ).firstOrNull { it.isNotBlank() && it != "null" }
                if (address != null) return@withContext address
            }
            null
        }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun request(url: String, service: String): JSONObject? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val data = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(data) }.getOrNull()
            val meta = json?.optJSONObject("meta")
            val apiCode = meta?.optInt("code", status) ?: status
            val errorType = meta?.optJSONObject("error")?.optString("type").orEmpty()

            if ((status == 404 || apiCode == 404) && errorType == "itemNotFound") {
                return null // Документированный ответ: совпадений нет
            }
            if (status == 404 && data.contains("Results not found", ignoreCase = true)) {
                return null
            }
            if (status == 401 || status == 403) {
                throw IOException("$service: доступ отклонён (HTTP $status). Проверьте ключ и разрешённые API.")
            }
            if (status == 429) {
                throw IOException("$service: достигнут лимит запросов (HTTP 429).")
            }
            if (status !in 200..299 || apiCode !in 200..299) {
                throw IOException("$service: ошибка HTTP $status (код API $apiCode).")
            }
            return json ?: throw IOException("$service: сервер вернул не JSON.")
        } catch (e: SocketTimeoutException) {
            throw IOException("$service: превышено время ожидания. Проверьте интернет.", e)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseAddresses(root: JSONObject?): List<GeocodedAddress> {
        val items = root?.optJSONObject("result")?.optJSONArray("items") ?: return emptyList()
        val result = mutableListOf<GeocodedAddress>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val point = item.optJSONObject("point")
                ?: item.optJSONObject("geometry")?.optJSONObject("centroid")
                ?: continue
            val lat = point.optDouble("lat", Double.NaN)
            val lon = point.optDouble("lon", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite() ||
                lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            val address = listOf(
                item.optString("full_address_name"),
                item.optString("full_name"),
                item.optString("address_name"),
                item.optString("name")
            ).firstOrNull { it.isNotBlank() && it != "null" } ?: continue
            val id = item.optString("id").ifBlank { "${lat}_${lon}" }
            if (result.none { it.id == id }) {
                result.add(GeocodedAddress(id, address, lat, lon))
            }
        }
        return result
    }
}
