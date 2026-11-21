package com.garasi.app.ui

import android.content.Context
import com.garasi.app.R
import com.garasi.app.data.Item
import com.garasi.app.platform.Clipboard
import com.garasi.app.platform.ShareHelper
import com.garasi.app.text.AiAnswer
import com.garasi.app.text.AiPrompt

/** The free "Ask AI" hand-off, shared by the edit and price screens. */
object AiActions {
    fun ask(ctx: Context, vm: ItemViewModel, item: Item, mode: AiPrompt.Mode, photoPaths: List<String>) {
        val lang = item.listingLang
        if ((mode == AiPrompt.Mode.TRANSLATE || mode == AiPrompt.Mode.IMPROVE) &&
            item.title(lang).isBlank() && item.description(lang).isBlank()) {
            vm.say(R.string.msg_nothing_to_send); return
        }
        val photos = when (mode) {
            AiPrompt.Mode.WRITE -> photoPaths.take(6)
            AiPrompt.Mode.PRICE -> photoPaths.take(2)
            else -> emptyList()
        }
        val prompt = AiPrompt.build(mode, item, lang, photos.size)
        vm.lastPrompt = prompt
        vm.aiTargetLang = if (mode == AiPrompt.Mode.TRANSLATE) lang.other else lang
        Clipboard.copy(ctx, "Garasi prompt", prompt)
        val ok = ShareHelper.shareToAi(ctx, prompt, photos, ctx.getString(R.string.ai_chooser_title))
        when {
            !ok -> vm.say(R.string.msg_no_ai_app)
            !ShareHelper.hasAnyAiApp(ctx) -> vm.say(R.string.msg_no_known_ai_app)
            else -> vm.say(R.string.msg_prompt_copied)
        }
    }

    /** Reads the clipboard and applies the AI's reply. Returns the parsed answer, or null on failure. */
    fun paste(ctx: Context, vm: ItemViewModel, item: Item): AiAnswer? {
        val lang = item.listingLang
        val clip = Clipboard.read(ctx)
        if (clip == null) { vm.say(R.string.msg_clipboard_empty); return null }
        if (clip == vm.lastPrompt || "Reply ONLY" in clip || "Balas HANYA" in clip) {
            vm.say(R.string.msg_clipboard_is_prompt); return null
        }
        val target = vm.aiTargetLang ?: lang
        val answer = vm.applyAiAnswer(clip, target)
        if (answer == null) { vm.say(R.string.msg_ai_parse_failed); return null }
        if (target != lang && (answer.title != null || answer.description != null)) vm.update { it.copy(listingLang = target) }
        vm.aiTargetLang = null
        vm.say(if (answer.hasMarketPrices) R.string.msg_ai_prices_applied else R.string.msg_ai_applied)
        return answer
    }
}
