package com.garasi.app.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.ui.graphics.Color
import com.garasi.app.ui.StatusPill
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.garasi.app.GarasiApp
import com.garasi.app.R
import com.garasi.app.data.Item
import com.garasi.app.data.ItemStatus
import com.garasi.app.data.Photo
import com.garasi.app.text.Money
import com.garasi.app.ui.Routes
import com.garasi.app.ui.statusLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class HomeRow(val item: Item, val cover: Photo?)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val g = app as GarasiApp
    val rows: StateFlow<List<HomeRow>> = combine(g.db.dao().observeItems(), g.db.dao().observeAllPhotos()) { items, photos ->
        val covers = photos.groupBy { it.itemId }.mapValues { (_, ps) -> ps.firstOrNull { it.isCover } ?: ps.minByOrNull { it.position } }
        items.map { HomeRow(it, covers[it.id]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun newItem(): Long {
        val s = g.settings.state.value
        return g.db.dao().insert(Item(listingLang = s.defaultLang, location = s.city))
    }
}

enum class SortMode(val label: Int) {
    NEWEST(R.string.sort_newest), OLDEST(R.string.sort_oldest),
    PRICE_HIGH(R.string.sort_price_high), PRICE_LOW(R.string.sort_price_low)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController, vm: HomeViewModel = viewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<ItemStatus?>(null) }
    var sort by rememberSaveable { mutableStateOf(SortMode.NEWEST) }
    var sortMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val shown = rows
        .filter { filter == null || it.item.status == filter }
        .filter { r ->
            query.isBlank() || listOf(r.item.titleId, r.item.titleEn, r.item.brand, r.item.model, r.item.variant)
                .any { it.contains(query.trim(), ignoreCase = true) }
        }
        .let { list ->
            when (sort) {
                SortMode.NEWEST -> list.sortedByDescending { it.item.createdAt }
                SortMode.OLDEST -> list.sortedBy { it.item.createdAt }
                SortMode.PRICE_HIGH -> list.sortedByDescending { it.item.askPrice }
                SortMode.PRICE_LOW -> list.sortedBy { if (it.item.askPrice == 0L) Long.MAX_VALUE else it.item.askPrice }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton({ nav.navigate(Routes.STATS) }) { Icon(Icons.Filled.BarChart, stringResource(R.string.stats_title)) }
                    IconButton({ nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, stringResource(R.string.settings_title)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { scope.launch { val id = vm.newItem(); nav.navigate(Routes.item(id)) } },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.add_item), fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (rows.isNotEmpty()) { SummaryStrip(rows); Spacer(Modifier.height(12.dp)) }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                Box {
                    IconButton({ sortMenu = true }) { Icon(Icons.Filled.Sort, stringResource(R.string.sort)) }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        SortMode.entries.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(stringResource(m.label), fontWeight = if (m == sort) FontWeight.Bold else null) },
                                onClick = { sort = m; sortMenu = false },
                            )
                        }
                    }
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(filter == null, { filter = null }, label = { Text(stringResource(R.string.filter_all, rows.size)) },
                        shape = CircleShape, colors = chipColors())
                }
                items(ItemStatus.entries) { s ->
                    val n = rows.count { it.item.status == s }
                    FilterChip(filter == s, { filter = if (filter == s) null else s },
                        label = { Text("${stringResource(statusLabel(s))} ($n)") }, shape = CircleShape, colors = chipColors())
                }
            }
            if (shown.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Inventory2, null, Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(if (rows.isEmpty()) R.string.home_empty else R.string.home_no_match),
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(158.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(shown, key = { it.item.id }) { row -> ItemCard(row) { nav.navigate(Routes.item(row.item.id)) } }
                }
            }
        }
    }
}

@Composable
private fun ItemCard(row: HomeRow, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Box {
            if (row.cover != null) {
                AsyncImage(
                    model = File(row.cover.path), contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.AddAPhoto, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.no_photos_yet), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            StatusPill(row.item.status, Modifier.padding(8.dp))
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                row.item.displayName.ifBlank { stringResource(R.string.untitled_item) },
                maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                minLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            val price = if (row.item.status == ItemStatus.SOLD && row.item.soldPrice > 0) row.item.soldPrice else row.item.askPrice
            Text(
                if (price > 0) Money.format(price) else stringResource(R.string.no_price),
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
                color = if (price > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryStrip(rows: List<HomeRow>) {
    val forSale = rows.count { it.item.status != ItemStatus.SOLD }
    val sold = rows.filter { it.item.status == ItemStatus.SOLD }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Row(Modifier.padding(vertical = 14.dp)) {
            SummaryCell(forSale.toString(), stringResource(R.string.summary_for_sale), Modifier.weight(1f))
            SummaryCell(sold.size.toString(), stringResource(R.string.summary_sold), Modifier.weight(1f))
            SummaryCell(Money.short(sold.sumOf { it.item.soldPrice }), stringResource(R.string.summary_earned), Modifier.weight(1.3f))
        }
    }
}

@Composable
private fun SummaryCell(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
    }
}

@Composable
fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
)
