package com.garasi.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garasi.app.data.ItemStatus
import com.garasi.app.data.ListingLang
import com.garasi.app.data.ThemeMode
import com.garasi.app.data.WeightPreset
import com.garasi.app.text.Money
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.text.DateFormat
import java.util.Date

@Composable
private fun PageColumn(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 860.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

// ------------------------------------------------------------------------------- sync

@Composable
fun SyncPage() = PageColumn {
    val pairing = remember(Store.data.token, Store.data.port) { SyncServer.pairing() }
    Text(S("d_sync_title"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    SectionCard(S("d_sync_scan_title"), icon = Icons.Filled.QrCode2) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(232.dp).background(Color.White).padding(12.dp)) { QrCode(pairing.toUri()) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(S("d_sync_steps"), style = MaterialTheme.typography.bodyMedium)
                Text(S("d_sync_manual"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer {
                    Column {
                        Text(S("d_sync_address", pairing.hosts.joinToString("  /  ")), fontFamily = FontFamily.Monospace)
                        Text(S("d_sync_code", Store.data.token), fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    SectionCard(S("d_sync_status_title"), icon = Icons.Filled.Wifi) {
        val err = SyncServer.error
        Text(
            when {
                err != null -> S("d_sync_error", err)
                SyncServer.running -> S("d_sync_listening", Store.data.port)
                else -> S("d_sync_stopped")
            },
            color = if (err != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            if (SyncServer.lastSync > 0) S("d_sync_last", DateFormat.getDateTimeInstance().format(Date(SyncServer.lastSync)), SyncServer.lastDevice)
            else S("d_sync_waiting"),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(S("d_sync_firewall"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!SyncServer.running) Btn(S("d_sync_start")) { SyncServer.start() }
            Btn(S("d_sync_new_code"), tonal = true) { Store.regenerateToken() }
        }
        Text(S("d_sync_new_code_tip"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QrCode(text: String) {
    val matrix = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    }
    Canvas(Modifier.fillMaxSize()) {
        val cell = size.minDimension / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

// ------------------------------------------------------------------------------- stats

@Composable
fun StatsPage() = PageColumn {
    val items = Store.snapshot.items
    val sold = items.filter { it.status == ItemStatus.SOLD }
    val days = sold.filter { it.soldAt > 0 }.map { (it.soldAt - it.createdAt) / 86_400_000.0 }
    Text(S("stats_title"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Stat(S("stat_sold"), sold.size.toString(), Modifier.weight(1f))
        Stat(S("stat_earned"), Money.format(sold.sumOf { it.soldPrice }), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Stat(S("stat_avg_days"), if (days.isEmpty()) "—" else S("days_value", days.average()), Modifier.weight(1f))
        Stat(S("stat_active"), items.count { it.status != ItemStatus.SOLD }.toString(), Modifier.weight(1f))
    }
    if (sold.isNotEmpty()) SectionCard(S("stat_by_platform"), icon = Icons.Filled.BarChart) {
        sold.groupingBy { it.soldPlatform }.eachCount().forEach { (p, n) ->
            Row {
                Text(if (p == null) S("sold_elsewhere") else S("platform_" + p.name.lowercase()), Modifier.weight(1f))
                Text(n.toString(), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) = SectionCard(label, modifier) {
    Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

// ------------------------------------------------------------------------------- settings

@Composable
fun SettingsPage() = PageColumn {
    val s = Store.settings
    Text(S("settings_title"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    SectionCard(S("default_listing_language"), icon = Icons.Filled.Settings) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ListingLang.entries.forEach { l -> Chip(s.defaultLang == l, S(if (l == ListingLang.ID) "lang_id" else "lang_en")) { Store.updateSettings { it.copy(defaultLang = l) } } }
        }
    }
    SectionCard(S("default_city")) {
        OutlinedTextField(s.city, { v -> Store.updateSettings { it.copy(city = v) } }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text(S("location_hint")) })
    }
    SectionCard(S("theme")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { t -> Chip(s.theme == t, S("theme_" + t.name.lowercase())) { Store.updateSettings { it.copy(theme = t) } } }
        }
    }
    SectionCard(S("weight_presets")) {
        s.weightPresets.forEachIndexed { i, p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(p.name, { v -> Store.updateSettings { st -> st.copy(weightPresets = st.weightPresets.toMutableList().also { it[i] = p.copy(name = v) }) } },
                    Modifier.weight(1f), singleLine = true, label = { Text(S("preset_name")) })
                OutlinedTextField(if (p.grams > 0) p.grams.toString() else "", { v ->
                    Store.updateSettings { st -> st.copy(weightPresets = st.weightPresets.toMutableList().also { it[i] = p.copy(grams = v.filter(Char::isDigit).take(6).toIntOrNull() ?: 0) }) }
                }, Modifier.width(140.dp), singleLine = true, label = { Text(S("grams")) })
                IconButton({ Store.updateSettings { st -> st.copy(weightPresets = st.weightPresets.filterIndexed { j, _ -> j != i }) } }) {
                    Icon(Icons.Filled.Delete, S("delete"))
                }
            }
        }
        TextButton({ Store.updateSettings { it.copy(weightPresets = it.weightPresets + WeightPreset("", 1000)) } }) { Text(S("add_preset")) }
    }
    SectionCard(S("d_data_folder"), icon = Icons.Filled.Folder) {
        Text(S("d_data_folder_tip", Store.dir.path), style = MaterialTheme.typography.bodyMedium)
        Btn(S("d_open_folder"), Icons.Filled.Folder, tonal = true) { Os.openFolder(Store.dir) }
    }
    Spacer(Modifier.width(1.dp))
    Text(S("d_about"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
