package com.garasi.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garasi.app.data.Item
import com.garasi.app.data.ItemStatus
import com.garasi.app.text.Money

/** Master-detail: item list on the left, editor on the right. */
@Composable
fun ItemsPage() {
    var selected by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<ItemStatus?>(null) }
    val all = Store.snapshot.items
    val shown = all
        .filter { filter == null || it.status == filter }
        .filter { i -> query.isBlank() || listOf(i.titleId, i.titleEn, i.brand, i.model, i.variant).any { it.contains(query.trim(), true) } }
        .sortedByDescending { it.createdAt }
    if (selected != null && all.none { it.uid == selected }) selected = null

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(400.dp).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Summary(all)
            TextField(
                query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text(S("search_hint")) },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Chip(filter == null, S("filter_all", all.size)) { filter = null } }
                items(ItemStatus.entries) { s ->
                    Chip(filter == s, "${S(statusKey(s))} (${all.count { it.status == s }})") { filter = if (filter == s) null else s }
                }
            }
            Btn(S("add_item"), Icons.Filled.Add, Modifier.fillMaxWidth()) { selected = Store.newItem().uid }
            if (shown.isEmpty()) {
                Text(S(if (all.isEmpty()) "home_empty" else "home_no_match"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(shown, key = { it.uid }) { item -> ItemRow(item, item.uid == selected) { selected = item.uid } }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val uid = selected
            if (uid == null) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.Inventory2, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(S("d_select_item"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else Editor(uid) { selected = null }
        }
    }
}

@Composable
fun Chip(selected: Boolean, label: String, onClick: () -> Unit) = FilterChip(
    selected, onClick, label = { Text(label) }, shape = CircleShape,
    colors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    ),
)

@Composable
private fun Summary(all: List<Item>) {
    val sold = all.filter { it.status == ItemStatus.SOLD }
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            listOf(
                all.count { it.status != ItemStatus.SOLD }.toString() to S("summary_for_sale"),
                sold.size.toString() to S("summary_sold"),
                Money.short(sold.sumOf { it.soldPrice }) to S("summary_earned"),
            ).forEach { (v, l) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(v, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    Text(l, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: Item, selected: Boolean, onClick: () -> Unit) {
    val cover = Store.photos(item.uid).firstOrNull()
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 0.dp else 1.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val bmp = cover?.let { PhotoTools.thumb(it.path, 160) }
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Filled.Inventory2, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.displayName.ifBlank { S("untitled_item") }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val price = if (item.status == ItemStatus.SOLD && item.soldPrice > 0) item.soldPrice else item.askPrice
                Text(if (price > 0) Money.format(price) else S("no_price"), fontWeight = FontWeight.Bold,
                    color = if (price > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(item.status)
        }
    }
}
