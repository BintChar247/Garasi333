package com.garasi.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garasi.app.data.Category
import com.garasi.app.data.Condition
import com.garasi.app.data.Item
import com.garasi.app.data.ItemStatus
import com.garasi.app.data.ListingLang
import com.garasi.app.data.Photo
import com.garasi.app.data.Platform
import com.garasi.app.platform.Marketplace
import com.garasi.app.quality.FixAction
import com.garasi.app.quality.QualityChecker
import com.garasi.app.quality.Verdict
import com.garasi.app.text.AiAnswerParser
import com.garasi.app.text.AiPrompt
import com.garasi.app.text.Money
import com.garasi.app.text.PriceCalculator
import com.garasi.app.text.Templates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder

/** Per-item editor state that isn't stored (which AI hand-off is pending, etc.). */
private class AiState {
    var lastPrompt by mutableStateOf<String?>(null)
    var target by mutableStateOf<ListingLang?>(null)
    var research by mutableStateOf<String?>(null)
}

@Composable
fun Editor(uid: String, onDeleted: () -> Unit) {
    val item = Store.item(uid) ?: return
    val photos = Store.photos(uid)
    val ai = remember(uid) { AiState() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 980.dp
        Column(Modifier.fillMaxSize().padding(start = 4.dp, end = 16.dp, top = 16.dp)) {
            Header(item, onDeleted)
            Spacer(Modifier.height(12.dp))
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1.15f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PhotosCard(item, photos); DetailsCard(item); TextCard(item, ai); Spacer(Modifier.height(24.dp))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    QualityCard(item, photos); PriceCard(item, ai); PostCard(item, photos); Spacer(Modifier.height(24.dp))
                }
            } else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PhotosCard(item, photos); QualityCard(item, photos); DetailsCard(item); TextCard(item, ai)
                PriceCard(item, ai); PostCard(item, photos); Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun upd(item: Item, f: (Item) -> Item) = Store.updateItem(item.uid, f)

// ------------------------------------------------------------------------------- header

@Composable
private fun Header(item: Item, onDeleted: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    var sold by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(item.displayName.ifBlank { S("new_item") }, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
        StatusPill(item.status)
        if (item.status == ItemStatus.DRAFT) TextButton({ upd(item) { it.copy(status = ItemStatus.READY) } }) { Text(S("mark_ready")) }
        if (item.status == ItemStatus.READY) TextButton({ upd(item) { it.copy(status = ItemStatus.DRAFT) } }) { Text(S("mark_draft")) }
        if (item.status != ItemStatus.SOLD) TextButton({ sold = true }) { Text(S("mark_sold")) }
        else TextButton({
            upd(item) { it.copy(status = if (Store.listings(item.uid).isNotEmpty()) ItemStatus.POSTED else ItemStatus.READY, soldPrice = 0, soldPlatform = null, soldAt = 0) }
        }) { Text(S("unmark_sold")) }
        IconButton({ confirmDelete = true }) { Icon(Icons.Filled.Delete, S("delete_item")) }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(S("delete_item_q")) }, text = { Text(S("d_delete_item_body")) },
        confirmButton = { TextButton({ confirmDelete = false; Store.deleteItem(item.uid); onDeleted() }) { Text(S("delete")) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(S("cancel")) } },
    )
    if (sold) SoldDialog(item) { sold = false }
}

// ------------------------------------------------------------------------------- photos

@Composable
private fun PhotosCard(item: Item, photos: List<Photo>) {
    val scope = rememberCoroutineScope()
    var selected by remember(item.uid) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    SectionCard(S("photos_count", photos.size, 10), icon = Icons.Filled.PhotoCamera) {
        if (photos.isEmpty()) Text(S("d_photos_tip"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow2 {
            photos.forEachIndexed { i, p ->
                val bmp = PhotoTools.thumb(p.path)
                Box(
                    Modifier.size(118.dp).clip(RoundedCornerShape(14.dp))
                        .border(if (selected == p.uid) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, MaterialTheme.colorScheme.surface), RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { selected = if (selected == p.uid) null else p.uid },
                ) {
                    if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (i == 0) Badge(S("cover"))
                        if (p.isFlaw) Badge(S("flaw"))
                    }
                    p.score?.let { sc ->
                        Text("$sc", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(CircleShape)
                                .background(if (sc >= 80) ScoreGreen else if (sc >= 60) ScoreAmber else ScoreRed).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            if (photos.size < 10) Box(
                Modifier.size(118.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(enabled = !busy) {
                        val files = Os.pickImages(S("d_pick_photos"))
                        if (files.isNotEmpty()) scope.launch {
                            busy = true
                            val n = withContext(Dispatchers.IO) { Store.addPhotos(item.uid, files) }
                            busy = false
                            if (n < files.size) Ui.say("msg_photo_import_failed")
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator()
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.AddPhotoAlternate, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(S("d_add_photos"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        val sel = photos.firstOrNull { it.uid == selected }
        if (sel != null) {
            val idx = photos.indexOf(sel)
            val problems = QualityChecker.photoProblems(sel)
            if (problems.isNotEmpty()) Text(problems.joinToString(" · ") { S(problemKey(it.name)) },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow2 {
                IconButton({ Store.movePhoto(sel, -1) }, enabled = idx > 0) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S("d_move_left")) }
                IconButton({ Store.movePhoto(sel, 1) }, enabled = idx < photos.lastIndex) { Icon(Icons.AutoMirrored.Filled.ArrowForward, S("d_move_right")) }
                if (idx != 0) TextButton({ Store.makeCover(sel) }) { Text(S("make_cover")) }
                TextButton({ Store.updatePhoto(sel.uid) { it.copy(isFlaw = !it.isFlaw, angle = if (!it.isFlaw) "flaw" else if (it.angle == "flaw") "" else it.angle) } }) {
                    Text(S(if (sel.isFlaw) "untag_flaw" else "tag_flaw"))
                }
                TextButton({
                    scope.launch {
                        val cropped = withContext(Dispatchers.IO) { PhotoTools.cropSquare(sel) }
                        if (cropped != null) Store.updatePhoto(sel.uid) { cropped } else Ui.say("msg_crop_failed")
                    }
                }) { Icon(Icons.Filled.Crop, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(S("d_crop_center")) }
                AngleMenu(item, sel)
                IconButton({ Store.deletePhoto(sel); selected = null }) { Icon(Icons.Filled.Delete, S("delete")) }
            }
        } else if (photos.isNotEmpty()) Text(S("d_photo_select_tip"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AngleMenu(item: Item, p: Photo) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton({ open = true }) { Text(if (p.angle.isBlank()) S("tag_angle") else S("angle_" + p.angle)) }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text(S("angle_none")) }, onClick = { Store.updatePhoto(p.uid) { it.copy(angle = "") }; open = false })
            com.garasi.app.text.ListingText.angles(item.category).forEach { a ->
                DropdownMenuItem(text = { Text(S("angle_$a")) }, onClick = {
                    Store.updatePhoto(p.uid) { it.copy(angle = a, isFlaw = a == "flaw" || it.isFlaw) }; open = false
                })
            }
        }
    }
}

private fun problemKey(name: String) = when (name) {
    "BLURRY" -> "p_blurry"; "SOFT" -> "p_soft"; "TOO_DARK" -> "p_dark"; "OVEREXPOSED" -> "p_bright"; "FLAT" -> "p_flat"
    "ITEM_TOO_SMALL" -> "p_small"; "ITEM_TOO_TIGHT" -> "p_tight"; "OFF_CENTER" -> "p_off_center"
    "BUSY_BACKGROUND" -> "p_busy"; "LOW_RES" -> "p_low_res"; else -> "p_other"
}

@Composable
fun Badge(text: String) = Text(
    text, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 7.dp, vertical = 2.dp),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow2(content: @Composable () -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }

// ------------------------------------------------------------------------------- details

@Composable
private fun DetailsCard(item: Item) {
    SectionCard(S("details_title"), icon = Icons.Filled.Info) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Picker(S("category"), S("cat_" + item.category.name.lowercase()), Category.entries.map { it to S("cat_" + it.name.lowercase()) }, Modifier.weight(1f)) { c ->
                upd(item) { it.copy(category = c) }
            }
            Field(item.brand, "brand", Modifier.weight(1f)) { v -> upd(item) { it.copy(brand = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(item.model, "model", Modifier.weight(1f)) { v -> upd(item) { it.copy(model = v) } }
            Field(item.variant, "variant", Modifier.weight(1f), hint = "variant_hint") { v -> upd(item) { it.copy(variant = v) } }
        }
        Text(S("condition"), style = MaterialTheme.typography.labelLarge)
        FlowRow2 {
            Condition.entries.forEach { c ->
                Chip(item.condition == c, S(conditionKey(c))) { upd(item) { it.copy(condition = c) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(item.yearBought, "year_bought", Modifier.weight(1f)) { v -> upd(item) { it.copy(yearBought = v.filter(Char::isDigit).take(4)) } }
            Field(item.usageAge, "usage_age", Modifier.weight(1f), hint = "usage_age_hint") { v -> upd(item) { it.copy(usageAge = v) } }
        }
        Field(item.flaws, "flaws", hint = "flaws_hint", single = false) { v -> upd(item) { it.copy(flaws = v) } }
        Field(item.included, "included", hint = "included_hint", single = false) { v -> upd(item) { it.copy(included = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(item.reasonSelling, "reason", Modifier.weight(1f), hint = "reason_hint") { v -> upd(item) { it.copy(reasonSelling = v) } }
            Field(item.location, "location", Modifier.weight(1f), hint = "location_hint") { v -> upd(item) { it.copy(location = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(item.shipping, "shipping", Modifier.weight(1f), hint = "shipping_hint") { v -> upd(item) { it.copy(shipping = v) } }
            Field(if (item.weightGrams > 0) item.weightGrams.toString() else "", "weight_grams", Modifier.weight(1f)) { v ->
                upd(item) { it.copy(weightGrams = v.filter(Char::isDigit).take(6).toIntOrNull() ?: 0) }
            }
        }
        FlowRow2 {
            Store.settings.weightPresets.forEach { p ->
                OutlinedButton({ upd(item) { it.copy(weightGrams = p.grams) } }) { Text("${p.name} · ${p.grams} g") }
            }
        }
    }
}

fun conditionKey(c: Condition) = when (c) {
    Condition.NEW -> "cond_new"; Condition.LIKE_NEW -> "cond_like_new"
    Condition.USED_GOOD -> "cond_used_good"; Condition.USED_FLAWS -> "cond_used_flaws"
}

@Composable
fun Field(value: String, label: String, modifier: Modifier = Modifier, hint: String? = null, single: Boolean = true, onChange: (String) -> Unit) =
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(), label = { Text(S(label)) },
        placeholder = if (hint != null) { { Text(S(hint)) } } else null,
        singleLine = single, minLines = if (single) 1 else 2,
    )

@Composable
fun MoneyField(value: Long, label: String, modifier: Modifier = Modifier, onChange: (Long) -> Unit) =
    OutlinedTextField(
        if (value > 0) value.toString() else "", { s -> onChange(s.filter(Char::isDigit).take(12).toLongOrNull() ?: 0L) },
        modifier.fillMaxWidth(), label = { Text(S(label)) }, prefix = { Text("Rp ") }, singleLine = true,
        supportingText = { if (value > 0) Text(Money.format(value)) },
    )

@Composable
fun <T> Picker(label: String, current: String, options: List<Pair<T, String>>, modifier: Modifier = Modifier, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(current, {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text(label) }, singleLine = true)
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(open, { open = false }) {
            options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { onPick(v); open = false }) }
        }
    }
}

// ------------------------------------------------------------------------------- text + AI

private enum class AiSite(val label: String, val url: (String) -> String) {
    CLAUDE("Claude", { p -> if (p.length < 6000) "https://claude.ai/new?q=" + enc(p) else "https://claude.ai/new" }),
    CHATGPT("ChatGPT", { p -> if (p.length < 6000) "https://chatgpt.com/?q=" + enc(p) else "https://chatgpt.com/" }),
    GEMINI("Gemini", { _ -> "https://gemini.google.com/app" }),
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

private var aiSite by mutableStateOf(AiSite.CLAUDE)

private fun askAi(item: Item, ai: AiState, mode: AiPrompt.Mode) {
    val lang = item.listingLang
    if ((mode == AiPrompt.Mode.TRANSLATE || mode == AiPrompt.Mode.IMPROVE) && item.title(lang).isBlank() && item.description(lang).isBlank()) {
        Ui.say("msg_nothing_to_send"); return
    }
    val prompt = AiPrompt.build(mode, item, lang, 0)
    ai.lastPrompt = prompt
    ai.target = if (mode == AiPrompt.Mode.TRANSLATE) lang.other else lang
    Os.copy(prompt)
    if (Os.browse(aiSite.url(prompt))) Ui.say("d_prompt_opened", aiSite.label) else Ui.say("d_prompt_copied_only")
}

private fun pasteAi(item: Item, ai: AiState) {
    val clip = Os.paste() ?: run { Ui.say("msg_clipboard_empty"); return }
    if (clip == ai.lastPrompt || "Reply ONLY" in clip || "Balas HANYA" in clip) { Ui.say("msg_clipboard_is_prompt"); return }
    val a = AiAnswerParser.parse(clip)
    if (a.isEmpty) { Ui.say("msg_ai_parse_failed"); return }
    val target = ai.target ?: item.listingLang
    upd(item) { i ->
        var n = i.withText(target, title = a.title, desc = a.description)
        a.condition?.let { n = n.copy(condition = it) }
        a.usedMin?.let { n = n.copy(aiUsedMin = it) }
        a.usedMax?.let { n = n.copy(aiUsedMax = it) }
        a.newPrice?.let { n = n.copy(newPrice = it) }
        a.priceLow?.let { n = n.copy(compareLow = it) }
        a.priceMedian?.let { n = n.copy(compareMedian = it) }
        a.priceHigh?.let { n = n.copy(compareHigh = it) }
        if (n.askPrice == 0L && a.usedMax != null) n = n.copy(askPrice = a.usedMax!!)
        if (target != i.listingLang && (a.title != null || a.description != null)) n = n.copy(listingLang = target)
        n
    }
    if (a.hasMarketPrices) ai.research = listOfNotNull(a.sources, a.note).joinToString("\n\n").ifBlank { null }
    ai.target = null
    Ui.say(if (a.hasMarketPrices) "msg_ai_prices_applied" else "msg_ai_applied")
}

@Composable
private fun TextCard(item: Item, ai: AiState) {
    val lang = item.listingLang
    SectionCard(S("listing_text_title"), icon = Icons.Filled.Description) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(S("listing_language"), style = MaterialTheme.typography.labelLarge)
            ListingLang.entries.forEach { l ->
                Chip(lang == l, S(if (l == ListingLang.ID) "lang_toggle_id" else "lang_toggle_en")) { upd(item) { it.copy(listingLang = l) } }
            }
        }
        val title = item.title(lang)
        OutlinedTextField(
            title, { v -> upd(item) { it.withText(lang, title = v.replace("\n", " ")) } }, Modifier.fillMaxWidth(),
            label = { Text(S("title")) }, singleLine = true, isError = title.length > Templates.TITLE_MAX,
            supportingText = { Text(S("char_count", title.length, Templates.TITLE_MAX)) },
        )
        val desc = item.description(lang)
        OutlinedTextField(
            desc, { v -> upd(item) { it.withText(lang, desc = v) } }, Modifier.fillMaxWidth(),
            label = { Text(S("description")) }, minLines = 8, supportingText = { Text(S("desc_count", desc.length)) },
        )
        FlowRow2 {
            Btn(S("build_title"), tonal = true) { upd(item) { it.withText(lang, title = Templates.buildTitle(it, lang)) } }
            Btn(S("use_template"), tonal = true) { upd(item) { it.withText(lang, desc = Templates.description(it, lang)) } }
            Btn(S("d_copy_other_lang"), icon = Icons.Filled.ContentCopy, tonal = true) {
                Os.copy(item.title(lang.other) + "\n\n" + item.description(lang.other)); Ui.say("copied_x", S("d_other_language"))
            }
        }
        HorizontalDivider()
        Text(S("ai_section"), style = MaterialTheme.typography.labelLarge)
        Text(S("d_ai_tip"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(S("d_ai_open_in"), style = MaterialTheme.typography.bodySmall)
            AiSite.entries.forEach { s -> Chip(aiSite == s, s.label) { aiSite = s } }
        }
        FlowRow2 {
            Btn(S("ask_ai"), Icons.Filled.AutoAwesome) { askAi(item, ai, AiPrompt.Mode.WRITE) }
            Btn(S("paste_ai"), Icons.Filled.ContentPaste, tonal = true) { pasteAi(item, ai) }
            Btn(S("translate_to", if (lang.other == ListingLang.ID) "🇮🇩" else "🇬🇧"), Icons.Filled.Translate, tonal = true) { askAi(item, ai, AiPrompt.Mode.TRANSLATE) }
            Btn(S("improve_text"), Icons.Filled.Star, tonal = true) { askAi(item, ai, AiPrompt.Mode.IMPROVE) }
        }
    }
}

// ------------------------------------------------------------------------------- quality

@Composable
private fun QualityCard(item: Item, photos: List<Photo>) {
    val r = QualityChecker.check(item, photos)
    val color = when (r.verdict) { Verdict.READY -> ScoreGreen; Verdict.GOOD -> ScoreAmber; Verdict.NEEDS_FIXING -> ScoreRed }
    SectionCard(S("quality_check"), icon = Icons.Filled.Insights) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { r.score / 100f }, modifier = Modifier.size(64.dp), color = color,
                    strokeWidth = 7.dp, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                Text("${r.score}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(S("appeal_score"), style = MaterialTheme.typography.labelLarge)
                Text(S(r.verdict.label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            }
        }
        if (r.issues.isEmpty()) Text(S("quality_all_good"))
        var showAll by remember { mutableStateOf(false) }
        (if (showAll) r.issues else r.topFixes).forEach { issue ->
            val args: Array<Any> = if (issue.pieces.isNotEmpty()) arrayOf(issue.pieces.joinToString(", ") { S(it.label) }) else issue.args.toTypedArray()
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(if (issue.penalty >= 8) ScoreRed else ScoreAmber))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(S(issue.type.message, *args), style = MaterialTheme.typography.bodyMedium)
                    when (issue.type.action) {
                        FixAction.BUILD_TITLE -> TextButton({ upd(item) { it.withText(it.listingLang, title = Templates.buildTitle(it)) } }) { Text(S(issue.type.action.label)) }
                        FixAction.USE_TEMPLATE -> TextButton({ upd(item) { it.withText(it.listingLang, desc = Templates.description(it)) } }) { Text(S(issue.type.action.label)) }
                        FixAction.MAKE_COVER -> issue.photoId?.let { _ ->
                            val best = photos.filter { it.score != null && !it.isFlaw }.maxByOrNull { it.score!! }
                            if (best != null) TextButton({ Store.makeCover(best) }) { Text(S(issue.type.action.label)) }
                        }
                        else -> {}
                    }
                }
            }
        }
        if (r.issues.size > 3) TextButton({ showAll = !showAll }) {
            Text(S(if (showAll) "hide_other_tips" else "show_other_tips", r.issues.size - 3))
        }
    }
}

// ------------------------------------------------------------------------------- price

@Composable
private fun PriceCard(item: Item, ai: AiState) {
    var query by remember(item.uid) { mutableStateOf(Marketplace.searchQuery(item)) }
    val median = PriceCalculator.effectiveMedian(item.compareLow, item.compareMedian, item.compareHigh)
    val suggestion = PriceCalculator.suggest(median, item.condition)
    SectionCard(S("price_title"), icon = Icons.Filled.Sell) {
        MoneyField(item.askPrice, "asking_price") { v -> upd(item) { it.copy(askPrice = v) } }
        if (item.aiUsedMin > 0) Text(S("ai_used_range", Money.format(item.aiUsedMin), Money.format(item.aiUsedMax)), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text(S("price_step1"), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(S("search_words")) }, singleLine = true)
        FlowRow2 {
            Platform.entries.forEach { p ->
                Btn(S("search_on", S("platform_" + p.name.lowercase())), Icons.AutoMirrored.Filled.OpenInNew, tonal = true) {
                    if (query.isNotBlank()) Os.browse(Marketplace.searchUrl(p, query)) else Ui.say("msg_enter_search")
                }
            }
        }
        Text(S("price_ai_title"), style = MaterialTheme.typography.labelLarge)
        FlowRow2 {
            Btn(S("price_ai_ask"), Icons.Filled.AutoAwesome) { askAi(item, ai, AiPrompt.Mode.PRICE) }
            Btn(S("price_ai_paste"), Icons.Filled.ContentPaste, tonal = true) { pasteAi(item, ai) }
        }
        ai.research?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall) } }
        Text(S("price_step2"), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyField(item.compareLow, "price_low", Modifier.weight(1f)) { v -> upd(item) { it.copy(compareLow = v) } }
            MoneyField(item.compareMedian, "price_median", Modifier.weight(1f)) { v -> upd(item) { it.copy(compareMedian = v) } }
            MoneyField(item.compareHigh, "price_high", Modifier.weight(1f)) { v -> upd(item) { it.copy(compareHigh = v) } }
        }
        if (suggestion != null) Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(S("price_suggestion"), style = MaterialTheme.typography.labelLarge)
            Text(Money.format(suggestion.askPrice), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(S("price_formula", Money.format(median), S(conditionKey(item.condition)),
                (PriceCalculator.conditionFactor(item.condition) * 100).toInt(), Money.format(suggestion.fairPrice)),
                style = MaterialTheme.typography.bodySmall)
            Btn(S("use_this_price")) { upd(item) { it.copy(askPrice = suggestion.askPrice) }; Ui.say("msg_price_set", Money.format(suggestion.askPrice)) }
        } else Text(S("price_need_median"), style = MaterialTheme.typography.bodySmall)
    }
}

// ------------------------------------------------------------------------------- post

@Composable
private fun PostCard(item: Item, photos: List<Photo>) {
    var platform by remember { mutableStateOf(Platform.TOKOPEDIA) }
    var both by remember { mutableStateOf(false) }
    var urlFor by remember { mutableStateOf<Platform?>(null) }
    val lang = item.listingLang
    val title = Marketplace.formatTitle(platform, item, lang)
    val desc = if (both) Marketplace.bothLanguages(platform, item) else Marketplace.formatDescription(platform, item, lang)
    val album = File(File(System.getProperty("user.home"), "Pictures"), "Garasi/" + albumName(item))
    SectionCard(S("post_title"), icon = Icons.Filled.Storefront) {
        FlowRow2 { Platform.entries.forEach { p -> Chip(platform == p, S("platform_" + p.name.lowercase())) { platform = p } } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(both, { both = it }); Text(S("copy_both_languages"), style = MaterialTheme.typography.bodyMedium)
        }
        Text(S("d_post_steps"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow2 {
            Btn("1. " + S("d_copy_title"), Icons.Filled.ContentCopy, tonal = true, enabled = title.isNotBlank()) { Os.copy(title); Ui.say("copied_x", S("label_title")) }
            Btn("2. " + S("d_copy_description"), Icons.Filled.ContentCopy, tonal = true, enabled = desc.isNotBlank()) { Os.copy(desc); Ui.say("copied_x", S("label_description")) }
            Btn("3. " + S("d_save_photos"), Icons.Filled.Folder, tonal = true, enabled = photos.isNotEmpty()) {
                album.mkdirs()
                photos.forEachIndexed { i, p -> runCatching { File(p.path).copyTo(File(album, "${albumName(item).replace(' ', '_')}_${i + 1}.jpg"), true) } }
                Os.openFolder(album); Ui.say("d_photos_saved", photos.size, album.path)
            }
            Btn("4. " + S("d_copy_price"), Icons.Filled.ContentCopy, tonal = true, enabled = item.askPrice > 0) { Os.copy(item.askPrice.toString()); Ui.say("copied_x", S("label_price")) }
        }
        Btn(S("d_open_seller", S("platform_" + platform.name.lowercase())), Icons.AutoMirrored.Filled.OpenInNew, Modifier.fillMaxWidth()) {
            Os.browse(Marketplace.spec(platform).sellWebUrl)
        }
        Text(S("category_hint", Marketplace.categoryHint(platform, item.category), S("cat_" + item.category.name.lowercase())), style = MaterialTheme.typography.bodySmall)
        Text(S("weight_line", if (item.weightGrams > 0) "${item.weightGrams} g" else S("not_set")), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text(S("where_posted"), style = MaterialTheme.typography.labelLarge)
        val listings = Store.listings(item.uid)
        Platform.entries.forEach { p ->
            val l = listings.firstOrNull { it.platform == p }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(S("platform_" + p.name.lowercase()), fontWeight = if (l != null) FontWeight.Bold else FontWeight.Normal)
                    if (l != null && l.url.isNotBlank()) Text(l.url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                if (l == null) TextButton({ urlFor = p }) { Text(S("mark_posted")) }
                else {
                    if (l.url.isNotBlank()) IconButton({ Os.browse(l.url) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, S("open")) }
                    TextButton({ Store.unmarkPosted(l) }) { Text(S("unmark")) }
                }
            }
        }
        if (item.status == ItemStatus.SOLD) Text(S("sold_for", Money.format(item.soldPrice)), fontWeight = FontWeight.Bold)
    }
    urlFor?.let { p ->
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { urlFor = null },
            title = { Text(S("posted_on", S("platform_" + p.name.lowercase()))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(S("paste_listing_url"))
                    OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(S("listing_url")) })
                    TextButton({ Os.paste()?.let { url = it.trim() } }) { Text(S("paste")) }
                }
            },
            confirmButton = { TextButton({ Store.markPosted(item.uid, p, url); urlFor = null }) { Text(S("save")) } },
            dismissButton = { TextButton({ urlFor = null }) { Text(S("cancel")) } },
        )
    }
}

fun albumName(item: Item) = item.displayName.ifBlank { "Item" }.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(40).ifBlank { "Item" }

@Composable
private fun SoldDialog(item: Item, onDone: () -> Unit) {
    var price by remember { mutableStateOf(item.askPrice) }
    val listings = Store.listings(item.uid)
    var platform by remember { mutableStateOf(listings.firstOrNull()?.platform) }
    var step by remember { mutableStateOf(0) }
    if (step == 0) AlertDialog(
        onDismissRequest = onDone,
        title = { Text(S("mark_sold")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyField(price, "final_price") { price = it }
                Text(S("sold_where"))
                FlowRow2 {
                    Platform.entries.forEach { p -> Chip(platform == p, S("platform_" + p.name.lowercase())) { platform = p } }
                    Chip(platform == null, S("sold_elsewhere")) { platform = null }
                }
            }
        },
        confirmButton = {
            TextButton({
                upd(item) { it.copy(status = ItemStatus.SOLD, soldPrice = price, soldPlatform = platform, soldAt = System.currentTimeMillis()) }
                if (listings.any { it.platform != platform && !it.removed }) step = 1 else { Ui.say("msg_sold_congrats"); onDone() }
            }) { Text(S("save")) }
        },
        dismissButton = { TextButton(onDone) { Text(S("cancel")) } },
    ) else AlertDialog(
        onDismissRequest = onDone,
        title = { Text(S("remove_other_listings")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(S("remove_other_listings_body"))
                Store.listings(item.uid).filter { it.platform != platform }.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(l.removed, { Store.setListingRemoved(l, it) })
                        Text(S("platform_" + l.platform.name.lowercase()), Modifier.weight(1f))
                        TextButton({ Os.browse(l.url.ifBlank { Marketplace.spec(l.platform).sellWebUrl }) }) { Text(S("open")) }
                    }
                }
            }
        },
        confirmButton = { TextButton({ Ui.say("msg_sold_congrats"); onDone() }) { Text(S("done")) } },
    )
}
