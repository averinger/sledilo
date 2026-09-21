package com.violetbit.app

import android.Manifest
import android.content.Intent
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
import kotlinx.coroutines.launch
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

        val work = PeriodicWorkRequestBuilder<UsageWorker>(1, TimeUnit.HOURS)
            .setInitialDelay(5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "usage_monitor",
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )

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

@Composable
fun UsageScreen(vm: UsageViewModel = viewModel()) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val hasPerm by vm.hasPerm.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var showInfo by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<AppUsage?>(null) }

    // Автообновление при возврате в приложение
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(48.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Следило",
                        color = Color(0xFFE0B0FF),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "кто сколько залипает",
                        color = Color(0xFF8A2BE2),
                        fontSize = 14.sp
                    )
                }
                // Кнопка инфо
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A004D))
                        .clickable { showInfo = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "i",
                        color = Color(0xFFE0B0FF),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            if (!hasPerm) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Нужно разрешение на\nдоступ к данным об использовании",
                            color = Color(0xFFE0B0FF),
                            fontSize = 16.sp,
                            modifier = Modifier.padding(20.dp)
                        )
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8A2BE2),
                                contentColor = Color(0xFFE0B0FF)
                            )
                        ) {
                            Text("Открыть настройки", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Выбери \"Следило\" в списке и дай доступ",
                            color = Color(0xFF6A0DAD),
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (apps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Пока статистики нет.\nЮзай телефон — данные появятся",
                        color = Color(0xFF6A0DAD),
                        fontSize = 16.sp
                    )
                }
            } else {
                val maxUsage = apps.maxOf { it.usageMillis }.coerceAtLeast(1L)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        UsageCard(
                            app = app,
                            maxUsage = maxUsage,
                            onClick = { selectedApp = app }
                        )
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }

    if (showInfo) {
        InfoDialog(onClose = { showInfo = false })
    }

    selectedApp?.let { app ->
        DetailDialog(app = app, vm = vm, onClose = { selectedApp = null })
    }
}

@Composable
fun UsageCard(app: AppUsage, maxUsage: Long, onClick: () -> Unit) {
    val isAbuser = app.usageMillis >= 5L * 60 * 60 * 1000
    val fraction = (app.usageMillis.toFloat() / maxUsage.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAbuser) Color(0xFF3A0050) else Color(0xFF141414)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    app.appName,
                    color = Color(0xFFE0B0FF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    UsageTracker.formatDuration(app.usageMillis),
                    color = if (isAbuser) Color(0xFFFF5555) else Color(0xFF8A2BE2),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            // Мини-график — прогресс-бар от максимума
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF2A004D))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(
                            if (isAbuser) Color(0xFFFF5555) else Color(0xFF8A2BE2)
                        )
                )
            }
        }
    }
}

@Composable
fun DetailDialog(app: AppUsage, vm: UsageViewModel, onClose: () -> Unit) {
    var hourly by remember { mutableStateOf<LongArray?>(null) }
    LaunchedEffect(app.packageName) {
        hourly = vm.getHourly(app.packageName)
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Column {
                Text(app.appName, color = Color(0xFFE0B0FF), fontWeight = FontWeight.Bold)
                Text(
                    UsageTracker.formatDuration(app.usageMillis) + " за сегодня",
                    color = Color(0xFF8A2BE2),
                    fontSize = 13.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Активность по часам:",
                    color = Color(0xFF8A2BE2),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                hourly?.let { HourlyChart(it) }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0ч", color = Color(0xFF6A0DAD), fontSize = 10.sp)
                    Text("12ч", color = Color(0xFF6A0DAD), fontSize = 10.sp)
                    Text("23ч", color = Color(0xFF6A0DAD), fontSize = 10.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Закрыть", color = Color(0xFFE0B0FF))
            }
        },
        containerColor = Color(0xFF141414)
    )
}

@Composable
fun HourlyChart(hourly: LongArray) {
    val maxVal = hourly.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val barWidth = size.width / 24f
        hourly.forEachIndexed { i, v ->
            val h = (v.toFloat() / maxVal.toFloat()) * size.height
            if (h > 0) {
                drawRect(
                    color = Color(0xFF8A2BE2),
                    topLeft = Offset(i * barWidth + barWidth * 0.1f, size.height - h),
                    size = Size(barWidth * 0.8f, h)
                )
            } else {
                // точка-заглушка
                drawRect(
                    color = Color(0xFF2A004D),
                    topLeft = Offset(i * barWidth + barWidth * 0.1f, size.height - 2f),
                    size = Size(barWidth * 0.8f, 2f)
                )
            }
        }
    }
}

@Composable
fun InfoDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "О приложении",
                    color = Color(0xFFE0B0FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                InfoBlock(
                    "📊 Что делает",
                    "Следило показывает, сколько времени ты проводишь в каждом приложении за сегодня. " +
                    "Если какое-то приложение юзается больше 5 часов — прилетает push с матным напоминанием. " +
                    "Зависит от времени суток: днём, вечером и ночью — разные тексты."
                )
                InfoBlock(
                    "⚙️ Как настроить",
                    "1. При первом запуске нажми «Открыть настройки»\n" +
                    "2. Найди «Следило» в списке приложений\n" +
                    "3. Дай разрешение «Разрешить доступ»\n" +
                    "4. Вернись сюда — список обновится сам"
                )
                InfoBlock(
                    "📱 Что на главном экране",
                    "• Каждая карточка — одно приложение\n" +
                    "• Полоса под названием — процент от максимума\n" +
                    "• Красный фон и цифра — приложение юзается 5+ часов\n" +
                    "• Тапни на карточку — увидишь почасовой график (0-23ч)"
                )
                InfoBlock(
                    "🔔 Про уведомления",
                    "Раз в час приложение проверяет всех нарушителей (>5ч). " +
                    "Если найдёт — прилетит пуш. Уведомления приходят даже когда приложение закрыто. " +
                    "Если пуша нет — проверь, что приложению разрешены уведомления в настройках Android."
                )
                InfoBlock(
                    "🔒 Приватность",
                    "Все данные хранятся только на этом устройстве. " +
                    "Нет интернета, нет серверов, нет аккаунтов, нет аналитики. " +
                    "Ничего никуда не отправляется. Совсем."
                )
                InfoBlock(
                    "💡 Совет",
                    "Если приложение не шлёт уведомления — зайди в настройки батареи и выбери " +
                    "для «Следило» режим «Без ограничений». Иначе Android его усыпит."
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Версия 1.0",
                    color = Color(0xFF6A0DAD),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
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

@Composable
fun InfoBlock(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            title,
            color = Color(0xFF8A2BE2),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            color = Color(0xFFE0B0FF),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}
