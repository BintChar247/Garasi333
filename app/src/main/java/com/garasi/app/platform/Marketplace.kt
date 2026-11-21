package com.garasi.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.garasi.app.data.Platform

/** Android side of [Marketplace]: launching apps and links. */
object MarketplaceLauncher {
    /**
     * Opens the marketplace at its sell screen if a known deep link resolves, else the app's home screen,
     * else the website. Returns which one was opened.
     */
    enum class Opened { DEEP_LINK, APP, WEB, NOTHING }

    fun openSell(context: Context, p: Platform): Opened {
        val spec = Marketplace.spec(p)
        val pm = context.packageManager
        val installed = spec.packages.firstOrNull { pkg -> pm.getLaunchIntentForPackage(pkg) != null }
        if (installed != null) {
            for (link in spec.sellDeepLinks) {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(installed)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (i.resolveActivity(pm) != null && start(context, i)) return Opened.DEEP_LINK
            }
            pm.getLaunchIntentForPackage(installed)?.let {
                if (start(context, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return Opened.APP
            }
        }
        return if (openUrl(context, spec.sellWebUrl)) Opened.WEB else Opened.NOTHING
    }

    fun openUrl(context: Context, url: String): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent); true
    } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }
}
