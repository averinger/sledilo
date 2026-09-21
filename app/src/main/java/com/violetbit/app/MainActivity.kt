package com.violetbit.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val usageWork = PeriodicWorkRequestBuilder<UsageWorker>(1, TimeUnit.HOURS)
            .setInitialDelay(5, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "usage_monitor", ExistingPeriodicWorkPolicy.KEEP, usageWork
        )

        val updateWork = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(10, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "update_checker", ExistingPeriodicWorkPolicy.KEEP, updateWork
        )

        SlediloWidget.refreshAll(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF0A0A0A),
                surface = Color(0xFF141414),
                primary = Color(0xFF8A2BE2),
                onPrimary = Color(0xFFE0B0FF),
                onBackground = Color(0xFFE0B0FF),
                onSurface = Color(0xFFE0B0FF)
            )) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0A)) {
                    UsageScreen()
                }
            }
        }
    }
}

val phraseTemplates = listOf(
    "Э, {app} уже {hours} ч. Хватит",
    "Ты в {app} уже {hours} часов, братан",
    "{app} — {hours} ч. Помойся уже",
    "Снова {app}? {hours} ч, совесть есть?",
    "Хватит {app}. {hours} часов — перебор",
    "Жопу поднял и закрыл {app}, {hours} ч уже",
    "Опять {app}? Иди делом займись",
    "Уже {hours} ч в {app}. Стыдно, братан",
    "Ну ты и хуйло. {app} забросил, да?",
    "{app} {hours} ч. Тебя там медом намазано?",
    "Ты в {app} {hours} ч. Ты вообще нормальный?",
    "Полночь, а ты всё {app} терроризируешь. {hours} ч"
)

@Composable
fun UsageScreen(vm: UsageViewModel = viewModel()) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val hasPerm by vm.hasPerm.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var showInfo by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<AppUsage?>(null) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var updateChecked by remember { mutableStateOf(false) }

    // Проверка обновлений при запуске
    LaunchedEffect(Unit) {
        if (!updateChecked) {
            updateChecked = true
            val info = withContext(Dispatchers.IO) { UpdateChecker.check() }
            if (info.available) updateInfo = info
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { vm.refresh() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFF0A0A0A), Color(0xFF2A004D), Color(0xFF0A0A0A))
    )

    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Spacer(Modifier.height(30.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(Color(0xFF2A004D)).clickable { showHistory = true },
                    contentAlignment = Alignment.Center
                ) { Text("📊", color = Color(0xFFE0B0FF), fontSize = 18.sp) }

                Spacer(Modifier.width(6.dp))

                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(Color(0xFF2A004D)).clickable { showSettings = true },
                    contentAlignment = Alignment.Center
                ) { Text("⚙", color = Color(0xFFE0B0FF), fontSize = 20.sp) }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Следило", color = Color(0xFFE0B0FF), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("порог: ${settings.thresholdHours} ч", color = Color(0xFF8A2BE2), fontSize = 12.sp)
                }

                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(Color(0xFF2A004D)).clickable { showInfo = true },
                    contentAlignment = Alignment.Center
                ) { Text("i", color = Color(0xFFE0B0FF), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(24.dp))
            if (!hasPerm) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Нужно разрешение на\nдоступ к данным об использовании",
                            color = Color(0xFFE0B0FF), fontSize = 16.sp, modifier = Modifier.padding(20.dp))
                        Button(
                            onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A2BE2), contentColor = Color(0xFFE0B0FF))
                        ) { Text("Открыть настройки", fontWeight = FontWeight.Bold) }
                    }
                }
            } else if (apps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Пока статистики нет.\nЮзай телефон — данные появятся", color = Color(0xFF6A0DAD), fontSize = 16.sp)
                }
            } else {
                val maxUsage = apps.maxOf { it.usageMillis }.coerceAtLeast(1L)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        UsageCard(app, maxUsage, settings.thresholdHours) { selectedApp = app }
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }

    if (showInfo) InfoDialog(onClose = { showInfo = false })
    if (showSettings) SettingsDialog(vm, onClose = { showSettings = false })
    if (showHistory) HistoryDialog(vm, onClose = { showHistory = false })
    selectedApp?.let { app -> DetailDialog(app, vm) { selectedApp = null } }

    updateInfo?.let { info ->
        UpdateDialog(
            info = info,
            onDismiss = { updateInfo = null },
            onOpen = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl))
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
                updateInfo = null
            }
        )
    }
}

@Composable
fun UpdateDialog(
    info: UpdateChecker.UpdateInfo,
    onDismiss: () -> Unit,
    onOpen: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("🎉 Обновление!", color = Color(0xFFE0B0FF), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Версия ${info.latestVersion}", color = Color(0xFF8A2BE2), fontSize = 14.sp)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Текущая версия: ${UpdateChecker.CURRENT_VERSION}", color = Color(0xFF6A0DAD), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                if (info.releaseNotes.isNotBlank()) {
                    Text("Что нового:", color = Color(0xFF8A2BE2), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        info.releaseNotes.take(600),
                        color = Color(0xFFE0B0FF),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                } else {
                    Text("Новая версия доступна на GitHub", color = Color(0xFFE0B0FF), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8A2BE2),
                    contentColor = Color(0xFFE0B0FF)
                )
            ) {
                Text("⬇ Скачать", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Позже", color = Color(0xFF8A2BE2))
            }
        },
        containerColor = Color(0xFF141414)
    )
}

@Composable
fun UsageCard(app: AppUsage, maxUsage: Long, thresholdHours: Int, onClick: () -> Unit) {
    val isAbuser = app.usageMillis >= thresholdHours * 60L * 60L * 1000L
    val fraction = (app.usageMillis.toFloat() / maxUsage.toFloat()).coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isAbuser) Color(0xFF3A0050) else Color(0xFF141414))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(app.appName, color = Color(0xFFE0B0FF), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(UsageTracker.formatDuration(app.usageMillis),
                    color = if (isAbuser) Color(0xFFFF5555) else Color(0xFF8A2BE2), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF2A004D))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(if (isAbuser) Color(0xFFFF5555) else Color(0xFF8A2BE2)))
            }
        }
    }
}

@Composable
fun DetailDialog(app: AppUsage, vm: UsageViewModel, onClose: () -> Unit) {
    var hourly by remember { mutableStateOf<LongArray?>(null) }
    LaunchedEffect(app.packageName) { hourly = vm.getHourly(app.packageName) }
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Column {
                Text(app.appName, color = Color(0xFFE0B0FF), fontWeight = FontWeight.Bold)
                Text(UsageTracker.formatDuration(app.usageMillis) + " за сегодня", color = Color(0xFF8A2BE2), fontSize = 13.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Активность по часам:", color = Color(0xFF8A2BE2), fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                hourly?.let { HourlyChart(it) }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0ч", color = Color(0xFF6A0DAD), fontSize = 10.sp)
                    Text("12ч", color = Color(0xFF6A0DAD), fontSize = 10.sp)
                    Text("23ч", color = Color(0xFF6A0DAD), fontSize = 10.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Закрыть", color = Color(0xFFE0B0FF)) } },
        containerColor = Color(0xFF141414)
    )
}

@Composable
fun HourlyChart(hourly: LongArray) {
    val maxVal = hourly.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        val barWidth = size.width / 24f
        hourly.forEachIndexed { i, v ->
            val h = (v.toFloat() / maxVal.toFloat()) * size.height
            if (h > 0) {
                drawRect(color = Color(0xFF8A2BE2),
                    topLeft = Offset(i * barWidth + barWidth * 0.1f, size.height - h),
                    size = Size(barWidth * 0.8f, h))
            } else {
                drawRect(color = Color(0xFF2A004D),
                    topLeft = Offset(i * barWidth + barWidth * 0.1f, size.height - 2f),
                    size = Size(barWidth * 0.8f, 2f))
            }
        }
    }
}

@Composable
fun SettingsDialog(vm: UsageViewModel, onClose: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var newPhrase by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }
    var showHourPicker by remember { mutableStateOf(false) }

    val installedApps = remember { UsageTracker.getInstalledApps(context) }

    val preview = newPhrase
        .replace("{app}", "TikTok")
        .replace("{hours}", settings.thresholdHours.toString())

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Настройки", color = Color(0xFFE0B0FF), fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                Text("Порог уведомлений", color = Color(0xFF8A2BE2), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${settings.thresholdHours} ч", color = Color(0xFFE0B0FF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Slider(
                        value = settings.thresholdHours.toFloat(),
                        onValueChange = { vm.setThreshold(it.toInt().coerceIn(1, 12)) },
                        valueRange = 1f..12f, steps = 10,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF8A2BE2),
                            activeTrackColor = Color(0xFF8A2BE2),
                            inactiveTrackColor = Color(0xFF2A004D)
                        )
                    )
                }
                Text("Если приложение юзается больше этого времени — прилетит пуш", color = Color(0xFF6A0DAD), fontSize = 11.sp)
                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Без мата", color = Color(0xFF8A2BE2), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Вежливые напоминания", color = Color(0xFF6A0DAD), fontSize = 11.sp)
                    }
                    Switch(
                        checked = settings.noProfanity,
                        onCheckedChange = { vm.setNoProfanity(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFE0B0FF), checkedTrackColor = Color(0xFF8A2BE2))
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Свои фразы", color = Color(0xFF8A2BE2), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.useCustomPhrases,
                        onCheckedChange = { vm.setUseCustomPhrases(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFE0B0FF), checkedTrackColor = Color(0xFF8A2BE2))
                    )
                }
                Text("Если включено — используются только твои фразы", color = Color(0xFF6A0DAD), fontSize = 11.sp)

                Spacer(Modifier.height(12.dp))
                Text("Готовые шаблоны (тапни чтобы добавить):", color = Color(0xFF8A2BE2), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                phraseTemplates.forEach { tpl ->
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E0030))
                            .clickable { vm.addPhrase(tpl) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) { Text("+ $tpl", color = Color(0xFFE0B0FF), fontSize = 12.sp) }
                    Spacer(Modifier.height(4.dp))
                }

                Spacer(Modifier.height(12.dp))
                Divider(color = Color(0xFF2A004D))
                Spacer(Modifier.height(12.dp))

                Text("Своя фраза:", color = Color(0xFF8A2BE2), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = newPhrase,
                    onValueChange = { newPhrase = it },
                    label = { Text("Введи текст") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFE0B0FF),
                        unfocusedTextColor = Color(0xFFE0B0FF),
                        focusedBorderColor = Color(0xFF8A2BE2),
                        unfocusedBorderColor = Color(0xFF4A0080),
                        focusedLabelColor = Color(0xFF8A2BE2),
                        unfocusedLabelColor = Color(0xFF8A2BE2)
                    )
                )

                Spacer(Modifier.height(6.dp))
                Text("Быстрая вставка:", color = Color(0xFF6A0DAD), fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = { newPhrase = "$newPhrase{app}" },
                        label = { Text("{app}", color = Color(0xFFE0B0FF), fontSize = 12.sp) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF2A004D))
                    )
                    AssistChip(
                        onClick = { newPhrase = "$newPhrase{hours}" },
                        label = { Text("{hours}", color = Color(0xFFE0B0FF), fontSize = 12.sp) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF2A004D))
                    )
                    AssistChip(
                        onClick = { showAppPicker = true },
                        label = { Text("📱 прил.", color = Color(0xFFE0B0FF), fontSize = 12.sp) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF2A004D))
                    )
                    AssistChip(
                        onClick = { showHourPicker = true },
                        label = { Text("🕐 часы", color = Color(0xFFE0B0FF), fontSize = 12.sp) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF2A004D))
                    )
                }

                if (newPhrase.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Пример: $preview", color = Color(0xFF8A2BE2), fontSize = 11.sp)
                }

                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { vm.addPhrase(newPhrase); newPhrase = "" },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A2BE2), contentColor = Color(0xFFE0B0FF))
                ) { Text("Добавить фразу", fontWeight = FontWeight.Bold) }

                Spacer(Modifier.height(12.dp))
                Text("Мои фразы (${settings.customPhrases.size}):", color = Color(0xFF8A2BE2), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (settings.customPhrases.isEmpty()) {
                    Text("Пока пусто", color = Color(0xFF6A0DAD), fontSize = 12.sp)
                } else {
                    settings.customPhrases.forEach { phrase ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("• $phrase", color = Color(0xFFE0B0FF), fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.removePhrase(phrase) }) { Text("✕", color = Color(0xFFFF5555), fontSize = 16.sp) }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Divider(color = Color(0xFF2A004D))
                Spacer(Modifier.height(16.dp))

                Text("Виджет", color = Color(0xFF8A2BE2), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Зажми пустое место на рабочем столе → Виджеты → Следило",
                    color = Color(0xFF6A0DAD), fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { SlediloWidget.refreshAll(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A004D),
                        contentColor = Color(0xFFE0B0FF)
                    )
                ) { Text("Обновить виджет", fontWeight = FontWeight.Bold) }

                Spacer(Modifier.height(16.dp))
                Divider(color = Color(0xFF2A004D))
                Spacer(Modifier.height(16.dp))

                Text("О проекте", color = Color(0xFF8A2BE2), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LinkRow("📖 Статья на VC.ru", "https://vc.ru/id6119281/3149535-sozdanie-trekera-ekrannogo-vremeni-na-android-bez-pk-i-google-play", context)
                LinkRow("📖 Статья на DTF", "https://dtf.ru/id3541098/5310474-prilozhenie-sledilo-dlya-kontrolya-vremeni-v-telefone", context)
                LinkRow("💻 Исходники на GitHub", "https://github.com/averinger/sledilo", context)
                LinkRow("⬇ Скачать APK", "https://github.com/averinger/sledilo/releases/latest", context)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Закрыть", color = Color(0xFFE0B0FF), fontWeight = FontWeight.Bold) } },
        containerColor = Color(0xFF141414)
    )

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("Выбери приложение", color = Color(0xFFE0B0FF)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(installedApps) { (_, name) ->
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    newPhrase = if (newPhrase.isBlank()) name else "$newPhrase $name"
                                    showAppPicker = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) { Text(name, color = Color(0xFFE0B0FF), fontSize = 14.sp) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAppPicker = false }) { Text("Отмена", color = Color(0xFF8A2BE2)) } },
            containerColor = Color(0xFF141414)
        )
    }

    if (showHourPicker) {
        AlertDialog(
            onDismissRequest = { showHourPicker = false },
            title = { Text("Выбери часы", color = Color(0xFFE0B0FF)) },
            text = {
                Column {
                    (1..12).forEach { h ->
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    newPhrase = if (newPhrase.isBlank()) "$h" else "$newPhrase $h"
                                    showHourPicker = false
                                }
                                .padding(vertical = 8.dp, horizontal = 8.dp)
                        ) { Text("$h ч", color = Color(0xFFE0B0FF), fontSize = 14.sp) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHourPicker = false }) { Text("Отмена", color = Color(0xFF8A2BE2)) } },
            containerColor = Color(0xFF141414)
        )
    }
}

@Composable
fun LinkRow(text: String, url: String, context: android.content.Context) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { Text(text, color = Color(0xFFE0B0FF), fontSize = 14.sp) }
}

@Composable
fun InfoDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("О приложении", color = Color(0xFFE0B0FF), fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                InfoBlock("📊 Что делает",
                    "Следило показывает, сколько времени ты проводишь в каждом приложении за сегодня. Если приложение юзается больше порога — прилетает push с матным напоминанием.")
                InfoBlock("⚙️ Как настроить",
                    "1. При первом запуске нажми «Открыть настройки»\n2. Найди «Следило» в списке приложений\n3. Дай разрешение\n4. Вернись сюда — список обновится сам")
                InfoBlock("🎛 Конструктор фраз",
                    "В настройках есть готовые шаблоны. Можно писать свои: {app} — название приложения, {hours} — часы. Кнопки «📱 прил.» и «🕐 часы» помогают вставить реальные значения.")
                InfoBlock("📲 Виджет",
                    "Зажми пустое место на рабочем столе → Виджеты → Следило. Показывает топ-3 приложения и общее время.")
                InfoBlock("🔄 Обновления",
                    "Приложение само проверяет новые версии на GitHub. Если найдёт — покажет диалог и пришлёт пуш.")
                InfoBlock("🔒 Приватность",
                    "Все данные только на устройстве. Нет интернета (кроме проверки обновлений), нет серверов, нет аккаунтов.")
                Text("Версия ${UpdateChecker.CURRENT_VERSION}",
                    color = Color(0xFF6A0DAD), fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Понял", color = Color(0xFFE0B0FF), fontWeight = FontWeight.Bold) } },
        containerColor = Color(0xFF141414)
    )
}

@Composable
fun InfoBlock(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(title, color = Color(0xFF8A2BE2), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = Color(0xFFE0B0FF), fontSize = 13.sp, lineHeight = 18.sp)
    }
}


@Composable
fun HistoryDialog(vm: UsageViewModel, onClose: () -> Unit) {
    val history by vm.history.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.refreshHistory()
    }

    val maxVal = history.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    val totalAll = history.sumOf { it.second }
    val avgMillis = if (history.isNotEmpty()) totalAll / history.size else 0L
    val activeDays = history.count { it.second > 0 }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text("История за 30 дней", color = Color(0xFFE0B0FF), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Активных дней", color = Color(0xFF8A2BE2), fontSize = 11.sp)
                        Text("$activeDays", color = Color(0xFFE0B0FF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Среднее в день", color = Color(0xFF8A2BE2), fontSize = 11.sp)
                        Text(formatShort(avgMillis), color = Color(0xFFE0B0FF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Divider(color = Color(0xFF2A004D))
                Spacer(Modifier.height(12.dp))

                if (history.all { it.second == 0L }) {
                    Text(
                        "Данных пока нет. Статистика появится, когда начнёшь пользоваться телефоном.",
                        color = Color(0xFF6A0DAD), fontSize = 13.sp
                    )
                } else {
                    Text("По дням:", color = Color(0xFF8A2BE2), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    history.forEach { (dateStr, millis) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                shortDate(dateStr),
                                color = Color(0xFF8A2BE2),
                                fontSize = 11.sp,
                                modifier = Modifier.width(48.dp)
                            )
                            Box(
                                modifier = Modifier.weight(1f).height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF2A004D))
                            ) {
                                val frac = (millis.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                                if (frac > 0f) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(frac).fillMaxHeight()
                                            .background(Brush.horizontalGradient(
                                                listOf(Color(0xFF8A2BE2), Color(0xFFE0B0FF))
                                            ))
                                    )
                                }
                            }
                            Text(
                                formatShort(millis),
                                color = Color(0xFFE0B0FF),
                                fontSize = 11.sp,
                                modifier = Modifier.width(60.dp).padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Закрыть", color = Color(0xFFE0B0FF), fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF141414)
    )
}

fun formatShort(millis: Long): String {
    val totalMin = millis / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}ч ${m}м" else "${m}м"
}

fun shortDate(dateStr: String): String {
    return try {
        val parts = dateStr.split("-")
        "${parts[2]}.${parts[1]}"
    } catch (e: Exception) { dateStr }
}
