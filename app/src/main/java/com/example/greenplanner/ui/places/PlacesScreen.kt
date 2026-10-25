package com.example.greenplanner.ui.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.greenplanner.data.local.PlaceEntity
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PlacesScreen(
    places: List<PlaceEntity>,
    onSave: (PlaceEntity) -> Unit,
    onDelete: (PlaceEntity) -> Unit
) {
    var editing by remember { mutableStateOf<PlaceEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PlaceEntity?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Мои места", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Сохрани дом, работу и другие места. Геоуведомления подключим на следующем шаге.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { editing = null; showEditor = true }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Добавить место")
        }
        Spacer(Modifier.height(16.dp))
        if (places.isEmpty()) {
            Text("Сохранённых мест пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(places, key = { it.id }) { place ->
                    Card(shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(place.name, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Координаты: %.6f, %.6f".format(Locale.US, place.latitude, place.longitude),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Радиус: ${place.radiusMeters} м", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { editing = place; showEditor = true }) { Text("Редактировать") }
                                TextButton(onClick = { deleting = place }) { Text("Удалить") }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showEditor) {
        PlaceEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { onSave(it); showEditor = false }
        )
    }
    deleting?.let { place ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить место?") },
            text = { Text("«${place.name}» будет удалено из сохранённых мест.") },
            confirmButton = {
                TextButton(onClick = { onDelete(place); deleting = null }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun PlaceEditorDialog(
    initial: PlaceEntity?,
    onDismiss: () -> Unit,
    onSave: (PlaceEntity) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var latitude by remember(initial?.id) { mutableStateOf(initial?.latitude?.toString().orEmpty()) }
    var longitude by remember(initial?.id) { mutableStateOf(initial?.longitude?.toString().orEmpty()) }
    var radius by remember(initial?.id) { mutableFloatStateOf((initial?.radiusMeters ?: 1000).toFloat()) }
    val latValue = latitude.trim().replace(',', '.').toDoubleOrNull()
    val lonValue = longitude.trim().replace(',', '.').toDoubleOrNull()
    val validLat = latValue != null && latValue in -90.0..90.0
    val validLon = lonValue != null && lonValue in -180.0..180.0
    val canSave = name.isNotBlank() && validLat && validLon

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новое место" else "Изменить место") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Название (Дом, Работа...)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = latitude, onValueChange = { latitude = it },
                    label = { Text("Широта (-90...90)") },
                    isError = latitude.isNotEmpty() && !validLat,
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = longitude, onValueChange = { longitude = it },
                    label = { Text("Долгота (-180...180)") },
                    isError = longitude.isNotEmpty() && !validLon,
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Радиус: ${radius.roundToInt()} м", fontWeight = FontWeight.SemiBold)
                Slider(value = radius, onValueChange = { radius = it },
                    valueRange = 100f..2000f, steps = 18)
                Text("На этом шаге координаты вводятся вручную. Выбор точки на карте добавим позже.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(enabled = canSave, onClick = {
                onSave(PlaceEntity(
                    id = initial?.id ?: 0L,
                    name = name.trim(),
                    latitude = requireNotNull(latValue),
                    longitude = requireNotNull(lonValue),
                    radiusMeters = radius.roundToInt()
                ))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
