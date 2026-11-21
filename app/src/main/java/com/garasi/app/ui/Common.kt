package com.garasi.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.garasi.app.R
import com.garasi.app.data.Category
import com.garasi.app.data.Condition
import com.garasi.app.data.ItemStatus
import com.garasi.app.data.ListingLang
import com.garasi.app.data.Platform
import kotlinx.coroutines.flow.Flow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.garasi.app.ui.theme.StatusDraft
import com.garasi.app.ui.theme.StatusPosted
import com.garasi.app.ui.theme.StatusReady
import com.garasi.app.ui.theme.StatusSold

@StringRes fun statusLabel(s: ItemStatus) = when (s) {
    ItemStatus.DRAFT -> R.string.status_draft
    ItemStatus.READY -> R.string.status_ready
    ItemStatus.POSTED -> R.string.status_posted
    ItemStatus.SOLD -> R.string.status_sold
}

@StringRes fun categoryLabel(c: Category) = when (c) {
    Category.ELECTRONICS -> R.string.cat_electronics
    Category.FASHION -> R.string.cat_fashion
    Category.BOOKS -> R.string.cat_books
    Category.TOYS -> R.string.cat_toys
    Category.HOME -> R.string.cat_home
    Category.HOBBY -> R.string.cat_hobby
    Category.OTHER -> R.string.cat_other
}

@StringRes fun conditionLabel(c: Condition) = when (c) {
    Condition.NEW -> R.string.cond_new
    Condition.LIKE_NEW -> R.string.cond_like_new
    Condition.USED_GOOD -> R.string.cond_used_good
    Condition.USED_FLAWS -> R.string.cond_used_flaws
}

@StringRes fun platformLabel(p: Platform) = when (p) {
    Platform.TOKOPEDIA -> R.string.platform_tokopedia
    Platform.SHOPEE -> R.string.platform_shopee
    Platform.OLX -> R.string.platform_olx
}

@StringRes fun langLabel(l: ListingLang) = when (l) {
    ListingLang.ID -> R.string.lang_id
    ListingLang.EN -> R.string.lang_en
}

@StringRes fun angleLabel(key: String) = when (key) {
    "front" -> R.string.angle_front
    "back" -> R.string.angle_back
    "sides" -> R.string.angle_sides
    "screen_on" -> R.string.angle_screen_on
    "label" -> R.string.angle_label
    "accessories" -> R.string.angle_accessories
    "detail" -> R.string.angle_detail
    "worn" -> R.string.angle_worn
    "spine" -> R.string.angle_spine
    "pages" -> R.string.angle_pages
    "box" -> R.string.angle_box
    "parts" -> R.string.angle_parts
    "in_use" -> R.string.angle_in_use
    "flaw" -> R.string.angle_flaw
    else -> R.string.angle_none
}

/** Shows ViewModel messages as snackbars. */
@Composable
fun CollectMessages(messages: Flow<UiMessage>, host: SnackbarHostState) {
    val ctx = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { m -> host.showSnackbar(ctx.getString(m.res, *m.args.toTypedArray())) }
    }
}

@Composable
fun SectionCard(
    title: String, modifier: Modifier = Modifier, icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.width(10.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

/** Primary = filled orange; outlined = soft tonal button. */
@Composable
fun BigButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, outlined: Boolean = false, enabled: Boolean = true) {
    val content: @Composable () -> Unit = {
        if (icon != null) { Icon(icon, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, maxLines = 1)
    }
    if (outlined) FilledTonalButton(onClick, modifier.heightIn(min = 50.dp), enabled = enabled) { content() }
    else Button(onClick, modifier.heightIn(min = 50.dp), enabled = enabled) { content() }
}

/** Small rounded status pill with a coloured dot. */
@Composable
fun StatusPill(status: ItemStatus, modifier: Modifier = Modifier) {
    val color = statusColor(status)
    Surface(modifier = modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f), shadowElevation = 1.dp) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(statusLabel(status)), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

fun statusColor(s: ItemStatus) = when (s) {
    ItemStatus.DRAFT -> StatusDraft
    ItemStatus.READY -> StatusReady
    ItemStatus.POSTED -> StatusPosted
    ItemStatus.SOLD -> StatusSold
}
