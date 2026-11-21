package com.garasi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.garasi.app.data.ItemStatus
import com.garasi.app.data.ThemeMode

/** Global one-line messages (snackbar). */
object Ui {
    var message by mutableStateOf<String?>(null)
    fun say(key: String, vararg args: Any) { message = S(key, *args) }
}

enum class Page(val label: String, val icon: ImageVector) {
    ITEMS("d_nav_items", Icons.Filled.Inventory2), STATS("stats_title", Icons.Filled.BarChart),
    SYNC("d_nav_sync", Icons.Filled.Sync), SETTINGS("settings_title", Icons.Filled.Settings),
}

fun main() {
    SyncServer.start()
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { Store.saveNow() } })
    application {
        Window(
            onCloseRequest = { Store.saveNow(); SyncServer.stop(); exitApplication() },
            title = S("d_window_title"),
            icon = painterResource("garasi/icon.png"),
            state = rememberWindowState(size = DpSize(1320.dp, 860.dp)),
        ) {
            val dark = when (Store.settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            GarasiTheme(dark) { App() }
        }
    }
}

@Composable
fun App() {
    var page by remember { mutableStateOf(Page.ITEMS) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Ui.message) { Ui.message?.let { snackbar.showSnackbar(it); Ui.message = null } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Row(Modifier.fillMaxSize().padding(pad).background(MaterialTheme.colorScheme.background)) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Spacer(Modifier.height(12.dp))
                Text("Garasi", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(20.dp))
                Page.entries.forEach { p ->
                    NavigationRailItem(
                        selected = page == p, onClick = { page = p },
                        icon = {
                            Box {
                                Icon(p.icon, null)
                                if (p == Page.SYNC && SyncServer.running) Box(
                                    Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(ScoreGreen)
                                )
                            }
                        },
                        label = { Text(S(p.label)) },
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                when (page) {
                    Page.ITEMS -> ItemsPage()
                    Page.STATS -> StatsPage()
                    Page.SYNC -> SyncPage()
                    Page.SETTINGS -> SettingsPage()
                }
            }
        }
    }
}

// ---------------------------------------------------------------- shared components

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
fun Btn(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, tonal: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, maxLines = 1)
    }
    if (tonal) FilledTonalButton(onClick, modifier.heightIn(min = 44.dp), enabled = enabled) { content() }
    else Button(onClick, modifier.heightIn(min = 44.dp), enabled = enabled) { content() }
}

@Composable
fun StatusPill(status: ItemStatus, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f), shadowElevation = 1.dp) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor(status)))
            Spacer(Modifier.width(6.dp))
            Text(S(statusKey(status)), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

fun statusKey(s: ItemStatus) = "status_" + s.name.lowercase()
