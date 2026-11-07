package com.example.greenplanner.integrations.twogis

import androidx.compose.foundation.verticalScroll

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.greenplanner.BuildConfig
import com.example.greenplanner.ui.theme.GreenPlannerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Отдельный экран выбора места. Здесь НЕ используется нативный SDK 2ГИС:
 * изображения приходят по HTTPS от Static API / Raster Tiles API.
 */
class TwoGisMapActivity : ComponentActivity() {
    companion object {
        const val EXTRA_LAT = "greenplanner.map.initial_lat"
        const val EXTRA_LON = "greenplanner.map.initial_lon"
        const val RESULT_LAT = "greenplanner.map.result_lat"
        const val RESULT_LON = "greenplanner.map.result_lon"
        const val EXTRA_ADDRESS_QUERY = "greenplanner.map.address_query"
        const val RESULT_ADDRESS = "greenplanner.map.result_address"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startLat = intent.getDoubleExtra(EXTRA_LAT, 55.751244)
        val startLon = intent.getDoubleExtra(EXTRA_LON, 37.618423)
        val startingAddress = intent.getStringExtra(EXTRA_ADDRESS_QUERY).orEmpty()
        setContent {
            GreenPlannerTheme {
                FullScreenTwoGisMap(
                    initialLat = startLat,
                    initialLon = startLon,
                    initialAddress = startingAddress,
                    onConfirm = { lat, lon, address ->
                        setResult(Activity.RESULT_OK, Intent().apply {
                            putExtra(RESULT_LAT, lat)
                            putExtra(RESULT_LON, lon)
                            // Передаём даже пустую строку, чтобы старый адрес в
                            // редакторе не остался у новой произвольной точки.
                            putExtra(RESULT_ADDRESS, address.orEmpty())
                        })
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}

/** Полноэкранный интерфейс, не Dialog и не WebView. */
@Composable
fun FullScreenTwoGisMap(
    initialLat: Double,
    initialLon: Double,
    initialAddress: String = "",
    onConfirm: (Double, Double, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val key = BuildConfig.DGIS_MAP_KEY.ifBlank { BuildConfig.DGIS_PLACES_KEY }
    var latitude by remember(initialLat) { mutableStateOf(initialLat.coerceIn(-85.0, 85.0)) }
    var longitude by remember(initialLon) { mutableStateOf(MapProjection.wrapLongitude(initialLon)) }
    var zoom by remember { mutableIntStateOf(16) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var attempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var image by remember { mutableStateOf<LoadedMap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var externalError by remember { mutableStateOf(false) }

    // Поиск можно выполнить на этом экране, не передвигая карту руками.
    val geocoder = remember(key) { TwoGisGeocoder(BuildConfig.DGIS_PLACES_KEY.ifBlank { key }) }
    val scope = rememberCoroutineScope()
    var addressQuery by remember(initialAddress) { mutableStateOf(initialAddress) }
    var addressResults by remember { mutableStateOf<List<GeocodedAddress>>(emptyList()) }
    var addressLoading by remember { mutableStateOf(false) }
    var addressError by remember { mutableStateOf<String?>(null) }
    // Адрес привязываем к точке только после выбора варианта из 2ГИС.
    var confirmedAddress by remember { mutableStateOf<String?>(null) }
    var confirmedAddressPoint by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Обратное геокодирование запускаем только после ручного выбора точки.
    // При вводе текста, загрузке тайлов и увеличении масштаба API не вызывается.
    var manualPointRevision by remember { mutableIntStateOf(0) }
    var pointAddress by remember { mutableStateOf<String?>(null) }
    var pointAddressStatus by remember { mutableStateOf("Выберите точку на карте, чтобы узнать адрес") }
    var pointAddressLoading by remember { mutableStateOf(false) }

    suspend fun searchAddress(query: String) {
        if (query.length < 4) {
            addressError = "Введите улицу и номер дома"
            return
        }
        addressLoading = true
        addressError = null
        addressResults = emptyList()
        try {
            val items = geocoder.findAddress(query, latitude, longitude)
            if (addressQuery.trim() == query) {
                addressResults = items
                if (items.isEmpty()) {
                    addressError = "Адрес не найден. Укажите город и номер дома, например: Новосибирск, Народная, 81."
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            addressError = e.localizedMessage ?: "Не удалось найти адрес"
        } finally {
            addressLoading = false
        }
    }

    // Если адрес написали в редакторе, переносим его на карту и ищем автоматически.
    LaunchedEffect(initialAddress) {
        if (initialAddress.trim().length >= 4) searchAddress(initialAddress.trim())
    }

    // Один запрос через 900 мс после ПОСЛЕДНЕГО касания или перетаскивания.
    // LaunchedEffect отменяет предыдущую попытку при выборе новой точки.
    LaunchedEffect(manualPointRevision) {
        if (manualPointRevision == 0) return@LaunchedEffect
        val requestedRevision = manualPointRevision
        val selectedLat = latitude
        val selectedLon = longitude
        pointAddressLoading = true
        pointAddressStatus = "Определяем адрес выбранной точки…"
        try {
            delay(900L)
            val found = geocoder.reverseAddress(selectedLat, selectedLon)
            if (manualPointRevision == requestedRevision) {
                pointAddress = found
                pointAddressStatus = if (found != null) {
                    "Ближайший адрес здания (в пределах 20 м) — проверьте его"
                } else {
                    "Адрес здания не найден. Можно сохранить координаты."
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (manualPointRevision == requestedRevision) {
                pointAddress = null
                pointAddressStatus = "Не удалось определить адрес: ${e.localizedMessage ?: "ошибка сети"}"
            }
        } finally {
            if (manualPointRevision == requestedRevision) {
                pointAddressLoading = false
            }
        }
    }

    LaunchedEffect(key, latitude, longitude, zoom, viewport, attempt) {
        if (viewport.width < 120 || viewport.height < 100) return@LaunchedEffect
        if (key.isBlank()) {
            error = "В local.properties не задан DGIS_MAP_KEY или DGIS_PLACES_KEY"
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            val size = MapProjection.requestSize(viewport)
            val loaded = withTimeout(17000L) {
                withContext(Dispatchers.IO) {
                    TwoGisImageLoader.load(key, latitude, longitude, zoom, size)
                }
            }
            image = loaded
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            error = "Превышено время ожидания ответа карты (17 секунд)."
            Log.w("GreenPlannerMap", "Map loading timeout")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.localizedMessage ?: "Не удалось загрузить карту."
            Log.w("GreenPlannerMap", "Map loading failed: ${e.javaClass.simpleName}")
        } finally {
            loading = false
        }
    }

    fun moveCenter(dx: Float, dy: Float) {
        if (viewport.width <= 0 || viewport.height <= 0) return
        val size = MapProjection.requestSize(viewport)
        val position = MapProjection.offset(
            latitude, longitude, zoom,
            dx * size.width / viewport.width.toDouble(),
            dy * size.height / viewport.height.toDouble()
        )
        image = null
        // После ручного переноса старый адрес не должен остаться привязанным
        // к другим координатам, даже если сдвинулись на пару метров.
        confirmedAddress = null
        confirmedAddressPoint = null
        pointAddress = null
        addressResults = emptyList()
        addressError = null
        latitude = position.first
        longitude = position.second
        manualPointRevision++
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("← Назад") }
                Text("Выбор точки — 2ГИС",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Введите адрес, выберите здание из списка или укажите точку на карте вручную.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Адрес над картой, определённый по последнему ручному выбору.
            Column(
                modifier = Modifier.fillMaxWidth().height(82.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE3F0E6))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Адрес выбранной точки", fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF275B3B))
                val displayedAddress = confirmedAddress ?: pointAddress
                if (displayedAddress != null) {
                    Text(displayedAddress, color = Color(0xFF275B3B),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(if (confirmedAddress == null) pointAddressStatus else "Адрес выбран",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            OutlinedTextField(
                value = addressQuery,
                onValueChange = {
                    addressQuery = it
                    confirmedAddress = null
                    confirmedAddressPoint = null
                    addressResults = emptyList()
                    addressError = null
                },
                label = { Text("Адрес: город, улица, дом") },
                placeholder = { Text("Новосибирск, Народная, 81") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { scope.launch { searchAddress(addressQuery.trim()) } },
                enabled = !addressLoading && addressQuery.trim().length >= 4,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (addressLoading) "Ищем адрес…" else "Найти адрес")
            }
            addressError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            if (addressResults.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 170.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Выберите найденное здание:",
                        style = MaterialTheme.typography.labelMedium)
                    addressResults.forEach { place ->
                        OutlinedButton(
                            onClick = {
                                addressQuery = place.address
                                confirmedAddress = place.address
                                confirmedAddressPoint = place.latitude to place.longitude
                                pointAddress = null
                                pointAddressStatus = "Адрес выбран из результатов поиска"
                                pointAddressLoading = false
                                manualPointRevision = 0 // отменяем ожидающую обратную геокодировку
                                addressResults = emptyList()
                                addressError = null
                                image = null
                                latitude = place.latitude
                                longitude = place.longitude
                                zoom = max(zoom, 17)
                                attempt++ // Обновляем и при совпадении с текущим центром
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(place.address, maxLines = 2) }
                    }
                }
            }
            confirmedAddress?.let {
                Text("Выбрано: $it", style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF397750))
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFE6EFE7))
                    .onSizeChanged { if (viewport != it) viewport = it },
                contentAlignment = Alignment.Center
            ) {
                val loaded = image
                if (loaded != null) {
                    val bitmap = remember(loaded.bitmap) { loaded.bitmap.asImageBitmap() }
                    var dragDelta by remember { mutableStateOf(Offset.Zero) }
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Изображение карты 2ГИС",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                            .pointerInput(latitude, longitude, zoom, viewport) {
                                detectTapGestures { position ->
                                    moveCenter(position.x - size.width / 2f,
                                        position.y - size.height / 2f)
                                }
                            }
                            .pointerInput(latitude, longitude, zoom, viewport) {
                                detectDragGestures(
                                    onDragStart = { dragDelta = Offset.Zero },
                                    onDragEnd = {
                                        if (dragDelta.getDistance() > 8f) {
                                            moveCenter(-dragDelta.x, -dragDelta.y)
                                        }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragDelta += amount
                                    }
                                )
                            }
                    )
                    Box(
                        modifier = Modifier.size(27.dp)
                            .background(Color(0xFF397750), CircleShape)
                            .border(4.dp, Color.White, CircleShape)
                    )
                    Column(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(onClick = { if (zoom < 18) { image = null; zoom++ } }, enabled = zoom < 18) {
                            Text("+")
                        }
                        Button(onClick = { if (zoom > 4) { image = null; zoom-- } }, enabled = zoom > 4) {
                            Text("−")
                        }
                    }
                    Text(
                        "Источник: ${loaded.source}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .padding(5.dp)
                    )
                }
                if (loading) {
                    Column(
                        modifier = Modifier.background(Color.White.copy(alpha = 0.84f), RoundedCornerShape(16.dp))
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Text("Загружаем карту…")
                        Text("Static API → Raster Tiles API",
                            style = MaterialTheme.typography.labelSmall)
                    }
                } else if (error != null) {
                    Column(
                        modifier = Modifier.background(Color.White.copy(alpha = 0.94f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Карта не загрузилась", style = MaterialTheme.typography.titleMedium)
                        Text(error.orEmpty(), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { attempt++ }) { Text("Повторить") }
                    }
                }
            }

            Text("Координаты: %.6f, %.6f".format(Locale.US, latitude, longitude),
                style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { attempt++ }, enabled = !loading,
                    modifier = Modifier.weight(1f)) {
                    Text("Обновить")
                }
                OutlinedButton(
                    onClick = { externalError = !openPointInTwoGis(context, latitude, longitude) },
                    modifier = Modifier.weight(1f)
                ) { Text("Открыть в 2ГИС") }
            }
            if (externalError) {
                Text("Не удалось открыть 2ГИС или браузер.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                Button(onClick = { onConfirm(latitude, longitude, confirmedAddress ?: pointAddress) },
                    enabled = image != null && !loading && error == null) {
                    Text("Выбрать")
                }
            }
        }
    }
}

private data class MapRequestSize(val width: Int, val height: Int)
private data class LoadedMap(val bitmap: Bitmap, val source: String)
private data class TileDownload(val x: Int, val y: Int, val bitmap: Bitmap?, val error: String?)

/** Прямое преобразование Web Mercator при масштабе карты из Raster Tiles API. */
private object MapProjection {
    private const val TILE = 256.0

    fun requestSize(viewport: IntSize): MapRequestSize {
        // Не превышаем ограничение API 1280x1280 и сохраняем соотношение сторон.
        val maxEdge = max(viewport.width, viewport.height).toDouble()
        // Уменьшенный запрос: меньше тайлов, быстрее загрузка на телефоне.
        val ratio = min(1.0, 700.0 / maxEdge)
        return MapRequestSize(
            (viewport.width * ratio).roundToInt().coerceIn(120, 1280),
            (viewport.height * ratio).roundToInt().coerceIn(90, 1280)
        )
    }

    fun wrapLongitude(lon: Double): Double = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

    fun centerPixels(lat: Double, lon: Double, zoom: Int): Pair<Double, Double> {
        val world = TILE * 2.0.pow(zoom)
        val limited = lat.coerceIn(-85.05112878, 85.05112878)
        val rad = Math.toRadians(limited)
        val x = (wrapLongitude(lon) + 180.0) / 360.0 * world
        val y = (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * world
        return x to y
    }

    fun offset(lat: Double, lon: Double, zoom: Int, dx: Double, dy: Double): Pair<Double, Double> {
        val world = TILE * 2.0.pow(zoom)
        val (x, y) = centerPixels(lat, lon, zoom)
        val newLat = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * ((y + dy) / world)))))
            .coerceIn(-85.05112878, 85.05112878)
        val newLon = wrapLongitude((x + dx) / world * 360.0 - 180.0)
        return newLat to newLon
    }
}

private object TwoGisImageLoader {
    suspend fun load(key: String, lat: Double, lon: Double, zoom: Int, size: MapRequestSize): LoadedMap {
        var staticError = ""
        try {
            val bitmap = staticImage(key, lat, lon, zoom, size)
            return LoadedMap(bitmap, "2ГИС Static")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            staticError = e.message.orEmpty()
            Log.w("GreenPlannerMap", "Static API: ${e.javaClass.simpleName}")
        }
        try {
            return LoadedMap(rasterImage(key, lat, lon, zoom, size), "2ГИС Raster")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val rasterError = e.message.orEmpty()
            Log.w("GreenPlannerMap", "Raster Tiles: ${e.javaClass.simpleName}")
            throw IllegalStateException("Static API: $staticError. Raster Tiles API: $rasterError")
        }
    }

    private fun staticImage(
        key: String, lat: Double, lon: Double, zoom: Int, size: MapRequestSize
    ): Bitmap {
        val coordinate = String.format(Locale.US, "%.7f,%.7f", lat, lon)
        val url = "https://static.maps.2gis.com/2.0?s=${size.width}x${size.height}" +
            "&c=$coordinate&z=$zoom&key=${URLEncoder.encode(key, "UTF-8")}"
        return downloadPng(url, key, "Static API")
    }

    private suspend fun rasterImage(
        key: String, lat: Double, lon: Double, zoom: Int, size: MapRequestSize
    ): Bitmap {
        val (centerX, centerY) = MapProjection.centerPixels(lat, lon, zoom)
        val left = centerX - size.width / 2.0
        val top = centerY - size.height / 2.0
        val xMin = floor(left / 256.0).toInt()
        val xMax = floor((left + size.width - 1.0) / 256.0).toInt()
        val yMin = floor(top / 256.0).toInt()
        val yMax = floor((top + size.height - 1.0) / 256.0).toInt()
        val tileCount = 1 shl zoom
        val map = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(map)
        canvas.drawColor(AndroidColor.rgb(230, 239, 231))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        // Запросы к тайлам выполняются ПАРАЛЛЕЛЬНО, а не последовательно.
        val requests = mutableListOf<Triple<Int, Int, String>>()
        for (ty in yMin..yMax) {
            if (ty < 0 || ty >= tileCount) continue
            for (tx in xMin..xMax) {
                val wrappedX = ((tx % tileCount) + tileCount) % tileCount
                val server = ((wrappedX + ty) % 5)
                val url = "https://tile$server.maps.2gis.com/v2/tiles/online_sd/" +
                    "$zoom/$wrappedX/$ty.png?key=${URLEncoder.encode(key, "UTF-8")}"
                requests.add(Triple(tx, ty, url))
            }
        }
        val downloads = coroutineScope {
            requests.map { (tx, ty, url) ->
                async(Dispatchers.IO) {
                    try {
                        TileDownload(tx, ty, downloadPng(url, key, "Raster Tiles API"), null)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        TileDownload(tx, ty, null, e.localizedMessage ?: e.javaClass.simpleName)
                    }
                }
            }.awaitAll()
        }
        val successes = downloads.filter { it.bitmap != null }
        if (successes.isEmpty()) {
            val reason = downloads.firstOrNull { it.error != null }?.error
                ?: "Нет доступных картографических тайлов"
            throw IllegalStateException(reason)
        }
        for (tile in downloads) {
            tile.bitmap?.let { bitmap ->
                canvas.drawBitmap(bitmap,
                    (tile.x * 256.0 - left).toFloat(),
                    (tile.y * 256.0 - top).toFloat(), paint)
                bitmap.recycle()
            }
        }
        if (successes.size * 2 < requests.size) {
            val reason = downloads.firstOrNull { it.error != null }?.error.orEmpty()
            throw IllegalStateException("Загрузилось ${successes.size} из ${requests.size} тайлов. $reason")
        }
        if (isFlatImage(map)) throw IllegalStateException("Raster Tiles API вернул пустую карту")
        return map
    }

    private fun isFlatImage(bitmap: Bitmap): Boolean {
        val colors = HashSet<Int>()
        for (row in 1..11) {
            for (column in 1..11) {
                val x = (bitmap.width - 1) * column / 12
                val y = (bitmap.height - 1) * row / 12
                val pixel = bitmap.getPixel(x, y)
                // Различия в несколько единиц RGB игнорируем.
                val coarseColor = (AndroidColor.red(pixel) shr 4 shl 8) or
                    (AndroidColor.green(pixel) shr 4 shl 4) or
                    (AndroidColor.blue(pixel) shr 4)
                colors.add(coarseColor)
                if (colors.size > 4) return false
            }
        }
        return true
    }

    private fun downloadPng(url: String, key: String, service: String): Bitmap {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3500
            readTimeout = 4500
            setRequestProperty("Accept", "image/png")
            instanceFollowRedirects = true
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                // Никогда не выводим URL и ключ пользователя в журнал или UI.
                val details = connection.errorStream?.bufferedReader()?.use { it.readText().take(220) }
                    ?.replace(key, "[KEY]")?.replace(Regex("https?://\\S+"), "[URL]")
                    .orEmpty()
                throw IllegalStateException("$service: HTTP $status${if (details.isNotBlank()) " ($details)" else ""}")
            }
            val body = connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 8_000_000) throw IllegalStateException("$service: ответ слишком большой")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val bitmap = BitmapFactory.decodeByteArray(body, 0, body.size)
                ?: throw IllegalStateException("$service вернул не PNG-изображение")
            if (bitmap.width < 100 || bitmap.height < 80) {
                throw IllegalStateException("$service вернул слишком маленькую карту")
            }
            // Защита от ложного успеха с однородной пустой картинкой.
            if (service == "Static API" && isFlatImage(bitmap)) {
                throw IllegalStateException("$service вернул почти однотонную карту без деталей")
            }
            return bitmap
        } finally {
            connection.disconnect()
        }
    }
}
