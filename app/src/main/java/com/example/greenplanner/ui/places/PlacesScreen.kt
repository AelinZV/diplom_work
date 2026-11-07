package com.example.greenplanner.ui.places

import android.Manifest
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.greenplanner.BuildConfig
import com.example.greenplanner.data.local.PlaceEntity
import com.example.greenplanner.integrations.twogis.GeocodedAddress
import com.example.greenplanner.integrations.twogis.TwoGisGeocoder
import com.example.greenplanner.integrations.twogis.FoundPlace
import com.example.greenplanner.integrations.twogis.TwoGisMapActivity
import com.example.greenplanner.integrations.twogis.TwoGisPlacesClient
import com.example.greenplanner.integrations.twogis.openPointInTwoGis
import com.example.greenplanner.location.CurrentLocationProvider
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Список мест остаётся на существующей вкладке «Мои места».
 * Новое: выбор координат через 2ГИС MapGL и поиск организаций через Places API.
 */
@Composable
fun PlacesScreen(
    places: List<PlaceEntity>,
    onSave: (PlaceEntity) -> Unit,
    onDelete: (PlaceEntity) -> Unit,
    monitoringMessage: String,
    onRecheck: () -> Unit
) {
    var editing by remember { mutableStateOf<PlaceEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PlaceEntity?>(null) }
    var showSearch by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Spacer(Modifier.height(12.dp))
                Text("Мои места", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Дом, работа, места родителей и другие точки для геонапоминаний.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                GeofencePermissionPanel(monitoringMessage, onRecheck)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { editing = null; showEditor = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Добавить место")
                }
                OutlinedButton(onClick = { showSearch = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Найти организации через 2ГИС")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (places.isEmpty()) {
            item { Text("Сохранённых мест пока нет. Добавьте «Дом» или «Работа», чтобы искать организации рядом.",
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(places, key = { it.id }) { place ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Координаты: %.6f, %.6f".format(Locale.US, place.latitude, place.longitude),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (place.address.isNotBlank()) {
                            Text(place.address, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (place.detail.isNotBlank()) {
                            Text(place.detail, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Радиус: ${place.radiusMeters} м", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editing = place; showEditor = true }) { Text("Редактировать") }
                            TextButton(onClick = { deleting = place }) { Text("Удалить") }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showEditor) {
        PlaceEditorDialog(
            initial = editing,
            defaultCenter = places.firstOrNull(),
            onDismiss = { showEditor = false },
            onSave = { onSave(it); showEditor = false }
        )
    }
    if (showSearch) {
        TwoGisSearchDialog(
            places = places,
            onDismiss = { showSearch = false },
            onSave = { onSave(it); showSearch = false }
        )
    }
    deleting?.let { place ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить место?") },
            text = { Text("«${place.name}» будет удалено из сохранённых мест. Связанные геонапоминания также будут сняты с отслеживания.") },
            confirmButton = { TextButton(onClick = { onDelete(place); deleting = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } }
        )
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun hasPreciseLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
private fun PlaceEditorDialog(
    initial: PlaceEntity?,
    defaultCenter: PlaceEntity?,
    onDismiss: () -> Unit,
    onSave: (PlaceEntity) -> Unit
) {
    val context = LocalContext.current
    val locationProvider = remember(context) { CurrentLocationProvider(context) }
    val cancellationSource = remember { CancellationTokenSource() }
    DisposableEffect(cancellationSource) { onDispose { cancellationSource.cancel() } }

    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    // Адрес сохраняется отдельно от названия. Уточнение (квартира/офис)
    // НЕ отправляется в 2ГИС: геозона отслеживает здание, а не помещение.
    var address by remember(initial?.id) { mutableStateOf(initial?.address.orEmpty()) }
    var detail by remember(initial?.id) { mutableStateOf(initial?.detail.orEmpty()) }
    var addressCandidates by remember(initial?.id) { mutableStateOf<List<GeocodedAddress>>(emptyList()) }
    var addressLoading by remember(initial?.id) { mutableStateOf(false) }
    var addressMessage by remember(initial?.id) { mutableStateOf<String?>(null) }
    var addressNeedsConfirmation by remember(initial?.id) { mutableStateOf(false) }
    val geocoder = remember { TwoGisGeocoder(BuildConfig.DGIS_PLACES_KEY) }
    val scope = rememberCoroutineScope()
    var latitude by remember(initial?.id) { mutableStateOf(initial?.latitude?.toString().orEmpty()) }
    var longitude by remember(initial?.id) { mutableStateOf(initial?.longitude?.toString().orEmpty()) }
    var radius by remember(initial?.id) { mutableFloatStateOf((initial?.radiusMeters ?: 1000).toFloat()) }
    var loadingLocation by remember(initial?.id) { mutableStateOf(false) }
    var locationStatus by remember(initial?.id) { mutableStateOf<String?>(null) }
    // Карта открывается отдельным Activity вместо второго Dialog.
    // Текущие поля формы сохраняются, пока экран карты открыт.
    val mapLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null && data.hasExtra(TwoGisMapActivity.RESULT_LAT) &&
                data.hasExtra(TwoGisMapActivity.RESULT_LON)) {
                latitude = "%.6f".format(Locale.US,
                    data.getDoubleExtra(TwoGisMapActivity.RESULT_LAT, 0.0))
                longitude = "%.6f".format(Locale.US,
                    data.getDoubleExtra(TwoGisMapActivity.RESULT_LON, 0.0))
                // Если на карте выбрали здание из подсказок, сохраняем его адрес.
                // При свободном выборе точки прежний текст не подменяем произвольно.
                // Пустая строка означает произвольную точку без найденного
                // здания: не сохраняем прежний адрес для новых координат.
                address = data.getStringExtra(TwoGisMapActivity.RESULT_ADDRESS).orEmpty()
                addressNeedsConfirmation = false
                addressCandidates = emptyList()
                locationStatus = "Точка выбрана. Проверьте координаты и сохраните место."
            }
        }
    }

    fun findCurrentLocation() {
        if (!hasLocationPermission(context)) {
            locationStatus = "Нет разрешения на геолокацию."
            return
        }
        loadingLocation = true
        locationStatus = "Определяем местоположение…"
        locationProvider.getCurrentLocation(cancellationSource.token) { result ->
            loadingLocation = false
            result.onSuccess { location ->
                latitude = "%.6f".format(Locale.US, location.latitude)
                longitude = "%.6f".format(Locale.US, location.longitude)
                addressNeedsConfirmation = false
                addressCandidates = emptyList()
                val accuracy = location.accuracy.roundToInt()
                locationStatus = if (hasPreciseLocationPermission(context)) {
                    "Координаты получены. Оценка точности: около $accuracy м."
                } else {
                    "Геопозиция приблизительная (погрешность около $accuracy м). Для геозон выберите точную геолокацию."
                }
            }.onFailure { error ->
                locationStatus = error.localizedMessage ?: "Не удалось определить координаты."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) findCurrentLocation()
        else locationStatus = "Доступ не предоставлен. Координаты можно задать вручную или на карте."
    }

    val latValue = latitude.trim().replace(',', '.').toDoubleOrNull()
    val lonValue = longitude.trim().replace(',', '.').toDoubleOrNull()
    val validLat = latValue != null && latValue in -90.0..90.0
    val validLon = lonValue != null && lonValue in -180.0..180.0
    val canSave = name.isNotBlank() && validLat && validLon &&
        !loadingLocation && !addressLoading && !addressNeedsConfirmation

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новое место" else "Изменить место") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Название (Дом, Работа…)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())

                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        addressCandidates = emptyList()
                        addressMessage = null
                        // Не допускаем сохранения старых координат для нового адреса.
                        addressNeedsConfirmation = it.isNotBlank()
                    },
                    label = { Text("Адрес (город, улица, дом)") },
                    placeholder = { Text("Новосибирск, ул. Ленина, 15") },
                    minLines = 1,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        addressLoading = true
                        addressMessage = null
                        addressCandidates = emptyList()
                        val requestedAddress = address.trim()
                        scope.launch {
                            try {
                                val found = geocoder.findAddress(
                                    requestedAddress,
                                    latValue?.takeIf { it in -90.0..90.0 } ?: defaultCenter?.latitude,
                                    lonValue?.takeIf { it in -180.0..180.0 } ?: defaultCenter?.longitude
                                )
                                // Ответ старого запроса не должен перекрыть изменённый адрес.
                                if (address.trim() == requestedAddress) {
                                    addressCandidates = found
                                    if (found.isEmpty()) addressMessage =
                                        "Адрес не найден. Попробуйте добавить город, например: Новосибирск, Народная, 81."
                                }
                            } catch (e: Exception) {
                                addressMessage = e.localizedMessage ?: "Не удалось выполнить поиск адреса"
                            } finally {
                                addressLoading = false
                            }
                        }
                    },
                    enabled = address.trim().length >= 5 && !addressLoading &&
                        BuildConfig.DGIS_PLACES_KEY.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (addressLoading) "Ищем адрес…" else "Найти адрес в 2ГИС")
                }
                if (BuildConfig.DGIS_PLACES_KEY.isBlank()) {
                    Text("Настройте DGIS_PLACES_KEY для поиска адреса, либо укажите координаты вручную.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                addressMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (addressCandidates.isNotEmpty()) {
                    Text("Выберите найденный адрес:", fontWeight = FontWeight.SemiBold)
                    addressCandidates.forEach { found ->
                        OutlinedButton(
                            onClick = {
                                address = found.address
                                latitude = "%.6f".format(Locale.US, found.latitude)
                                longitude = "%.6f".format(Locale.US, found.longitude)
                                addressNeedsConfirmation = false
                                addressCandidates = emptyList()
                                addressMessage = "Адрес найден, координаты заполнены."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(found.address) }
                    }
                }
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("Подъезд, квартира, офис или ориентир") },
                    placeholder = { Text("Подъезд 2, квартира 15") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Можно также выбрать точку на карте, использовать геопозицию или ввести координаты.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = {
                        if (hasLocationPermission(context)) findCurrentLocation()
                        else permissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    },
                    enabled = !loadingLocation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loadingLocation) "Определяем координаты…" else "📍 Использовать моё местоположение")
                }
                OutlinedButton(
                    onClick = {
                        val startLat = latValue?.takeIf { it in -90.0..90.0 }
                            ?: defaultCenter?.latitude ?: 55.751244
                        val startLon = lonValue?.takeIf { it in -180.0..180.0 }
                            ?: defaultCenter?.longitude ?: 37.618423
                        val intent = Intent(context, TwoGisMapActivity::class.java).apply {
                            putExtra(TwoGisMapActivity.EXTRA_LAT, startLat)
                            putExtra(TwoGisMapActivity.EXTRA_LON, startLon)
                            // Текст адреса доступен и внутри полноэкранной карты.
                            if (addressNeedsConfirmation) {
                                putExtra(TwoGisMapActivity.EXTRA_ADDRESS_QUERY, address.trim())
                            }
                        }
                        mapLauncher.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Выбрать на карте 2ГИС") }
                locationStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedTextField(value = latitude, onValueChange = {
                    latitude = it
                    addressNeedsConfirmation = false
                },
                    label = { Text("Широта (-90…90)") }, isError = latitude.isNotEmpty() && !validLat,
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = longitude, onValueChange = {
                    longitude = it
                    addressNeedsConfirmation = false
                },
                    label = { Text("Долгота (-180…180)") }, isError = longitude.isNotEmpty() && !validLon,
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (addressNeedsConfirmation) {
                    Text("Проверьте адрес через 2ГИС или укажите координаты самостоятельно.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                Text("Радиус: ${radius.roundToInt()} м", fontWeight = FontWeight.SemiBold)
                Slider(value = radius, onValueChange = { radius = it }, valueRange = 100f..2000f, steps = 18)
                Text("Выбор точки и поиск организаций работают через 2ГИС. Геонапоминания по-прежнему выполняются Android Geofencing API.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(enabled = canSave, onClick = {
                onSave(PlaceEntity(
                    id = initial?.id ?: 0L,
                    name = name.trim(),
                    latitude = requireNotNull(latValue),
                    longitude = requireNotNull(lonValue),
                    radiusMeters = radius.roundToInt(),
                    address = address.trim(),
                    detail = detail.trim()
                ))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )


}

@Composable
private fun TwoGisSearchDialog(
    places: List<PlaceEntity>,
    onDismiss: () -> Unit,
    onSave: (PlaceEntity) -> Unit
) {
    val client = remember { TwoGisPlacesClient(BuildConfig.DGIS_PLACES_KEY) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableLongStateOf(places.firstOrNull()?.id ?: -1L) }
    var query by remember { mutableStateOf("аптека") }
    var radius by remember { mutableFloatStateOf(3000f) }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<FoundPlace>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var mapOpeningError by remember { mutableStateOf<String?>(null) }
    val center = places.firstOrNull { it.id == selectedId }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(12.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Организации рядом — 2ГИС", style = MaterialTheme.typography.titleMedium)
                Text("Выберите место, рядом с которым искать.", style = MaterialTheme.typography.bodySmall)
                if (places.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        places.forEach { place ->
                            FilterChip(
                                selected = selectedId == place.id,
                                onClick = { selectedId = place.id; results = emptyList(); message = null },
                                label = { Text(place.name) }
                            )
                        }
                    }
                } else {
                    Text("Сначала добавьте сохранённое место (например, «Дом»).", color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("Что ищем (аптека, фитнес, магазин…)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text("Радиус поиска: ${radius.roundToInt()} м", style = MaterialTheme.typography.bodySmall)
                Slider(value = radius, onValueChange = { radius = it }, valueRange = 500f..10000f, steps = 18)
                Button(
                    onClick = {
                        val searchCenter = center ?: return@Button
                        loading = true
                        message = null
                        results = emptyList()
                        scope.launch {
                            try {
                                results = client.search(query, searchCenter.latitude,
                                    searchCenter.longitude, radius.roundToInt())
                                message = if (results.isEmpty()) "Ничего не найдено. Попробуйте увеличить радиус." else null
                            } catch (e: Exception) {
                                message = e.localizedMessage ?: "Ошибка запроса к 2ГИС"
                            } finally { loading = false }
                        }
                    },
                    enabled = center != null && query.isNotBlank() && !loading && BuildConfig.DGIS_PLACES_KEY.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (loading) "Поиск…" else "Найти") }
                if (BuildConfig.DGIS_PLACES_KEY.isBlank()) {
                    Text("Чтобы пользоваться поиском, добавьте DGIS_PLACES_KEY в local.properties.",
                        color = MaterialTheme.colorScheme.error)
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                mapOpeningError?.let { Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.id }) { place ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE5F0))
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(place.name, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(place.address, style = MaterialTheme.typography.bodySmall)
                                Text("По прямой: ${place.distanceMeters} м от «${center?.name.orEmpty()}»",
                                    style = MaterialTheme.typography.bodySmall)
                                Button(
                                    onClick = {
                                        onSave(PlaceEntity(
                                            name = place.name,
                                            latitude = place.latitude,
                                            longitude = place.longitude,
                                            radiusMeters = 500,
                                            address = place.address
                                        ))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Сохранить как место") }
                                OutlinedButton(
                                    onClick = {
                                        // Открываем реальную точку в 2ГИС/браузере.
                                        // Это работает независимо от WebGL внутри GreenPlanner.
                                        mapOpeningError = if (openPointInTwoGis(context, place.latitude, place.longitude))
                                            null else "Не удалось открыть 2ГИС или браузер."
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Показать на карте") }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
            }
        }
    }
}
