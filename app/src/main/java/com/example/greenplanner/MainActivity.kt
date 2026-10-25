package com.example.greenplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.greenplanner.data.local.TaskEntity
import com.example.greenplanner.ui.tasks.TaskViewModel
import com.example.greenplanner.ui.theme.GreenPlannerTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Screen(val caption: String, val symbol: String) {
    TODAY("Сегодня", "⌂"), ALL("Задачи", "▦"), ASSISTANT("Ассистент", "✦"), SETTINGS("Настройки", "⚙")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GreenPlannerTheme { PlannerApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlannerApp(vm: TaskViewModel = viewModel()) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    var activeScreen by remember { mutableStateOf(Screen.TODAY) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (activeScreen == Screen.TODAY) "Мой план" else activeScreen.caption, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = activeScreen == screen,
                        onClick = { activeScreen = screen },
                        icon = { Text(screen.symbol, fontSize = 22.sp) },
                        label = { Text(screen.caption, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer)
                    )
                }
            }
        },
        floatingActionButton = {
            if (activeScreen == Screen.TODAY || activeScreen == Screen.ALL) {
                FloatingActionButton(onClick = { editing = null; showEditor = true }) { Text("+", fontSize = 30.sp) }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (activeScreen) {
                Screen.TODAY -> TaskOverview(
                    heading = "Сегодня",
                    subtitle = today.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("ru"))),
                    tasks = tasks.filter { it.date == today.toString() },
                    emptyText = "Сегодня задач пока нет",
                    onToggle = vm::toggle,
                    onDelete = vm::delete,
                    onEdit = { editing = it; showEditor = true }
                )
                Screen.ALL -> Column {
                    // Календарный выбор дня; задачи можно открыть также по кнопке «Все».
                    var onlySelected by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { selectedDate = selectedDate.minusDays(1); onlySelected = true }) { Text("←") }
                        Text(selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), modifier = Modifier.weight(1f))
                        TextButton(onClick = { selectedDate = selectedDate.plusDays(1); onlySelected = true }) { Text("→") }
                        TextButton(onClick = { onlySelected = !onlySelected }) { Text(if (onlySelected) "Все" else "День") }
                    }
                    TaskOverview(
                        heading = if (onlySelected) "Задачи на день" else "Все задачи",
                        subtitle = if (onlySelected) selectedDate.toString() else "Запланированные дела",
                        tasks = if (onlySelected) tasks.filter { it.date == selectedDate.toString() } else tasks,
                        emptyText = "Нет задач для отображения",
                        onToggle = vm::toggle,
                        onDelete = vm::delete,
                        onEdit = { editing = it; showEditor = true }
                    )
                }
                Screen.ASSISTANT -> InfoScreen("ИИ-ассистент", "Добавим голосовой ввод и распознавание команд на следующем этапе.")
                Screen.SETTINGS -> InfoScreen("Мои места", "Позже добавим дом, работу и геозависимые уведомления.")
            }
        }
    }

    if (showEditor) {
        TaskEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { vm.save(it); showEditor = false }
        )
    }
}

@Composable
private fun TaskOverview(
    heading: String,
    subtitle: String,
    tasks: List<TaskEntity>,
    emptyText: String,
    onToggle: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onEdit: (TaskEntity) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text(heading, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        val done = tasks.count { it.completed }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Ваш прогресс", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Выполнено $done из ${tasks.size}", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (tasks.isEmpty()) Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tasks, key = { it.id }) { task ->
                TaskCard(task, { onToggle(task) }, { onEdit(task) }, { onDelete(task) })
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun TaskCard(task: TaskEntity, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.completed, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.SemiBold,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None)
                if (task.note.isNotBlank()) Text(task.note, style = MaterialTheme.typography.bodySmall)
                Text("${task.date} • ${task.category} • ${priorityName(task.priority)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                TextButton(onClick = onEdit) { Text("Изм.") }
                TextButton(onClick = onDelete) { Text("Удал.") }
            }
        }
    }
}

private fun priorityName(value: Int) = when(value) { 0 -> "Низкий"; 2 -> "Высокий"; else -> "Средний" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorDialog(initial: TaskEntity?, onDismiss: () -> Unit, onSave: (TaskEntity) -> Unit) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var note by remember(initial?.id) { mutableStateOf(initial?.note.orEmpty()) }
    var dateText by remember(initial?.id) { mutableStateOf(initial?.date ?: LocalDate.now().toString()) }
    var category by remember(initial?.id) { mutableStateOf(initial?.category ?: "Личное") }
    var priority by remember(initial?.id) { mutableIntStateOf(initial?.priority ?: 1) }
    var dateError by remember(initial?.id) { mutableStateOf(false) }
    val categories = listOf("Личное", "Учёба", "Работа", "Здоровье", "Покупки")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новая задача" else "Редактирование") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Описание") },
                    maxLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = dateText, onValueChange = { dateText = it; dateError = false },
                    label = { Text("Дата (ГГГГ-ММ-ДД)") }, isError = dateError,
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (dateError) Text("Некорректная дата, например 2026-10-25", color = MaterialTheme.colorScheme.error)
                Text("Категория", fontWeight = FontWeight.SemiBold)
                // Горизонтальная прокрутка, чтобы диалог помещался на небольших экранах.
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { menuOpen = true }) { Text(category + " ▾") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        categories.forEach { choice ->
                            DropdownMenuItem(text = { Text(choice) }, onClick = { category = choice; menuOpen = false })
                        }
                    }
                }
                Text("Приоритет", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0..2).forEach { level ->
                        FilterChip(selected = priority == level, onClick = { priority = level },
                            label = { Text(priorityName(level), fontSize = 11.sp) })
                        Spacer(Modifier.width(2.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = title.isNotBlank(), onClick = {
                val parsed = runCatching { LocalDate.parse(dateText) }.getOrNull()
                if (parsed == null) dateError = true
                else onSave(TaskEntity(
                    id = initial?.id ?: 0L, title = title.trim(), note = note.trim(),
                    date = parsed.toString(), category = category, priority = priority,
                    completed = initial?.completed ?: false
                ))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun InfoScreen(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center) { Text("✦", fontSize = 28.sp) }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
