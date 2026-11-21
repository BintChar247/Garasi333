package com.garasi.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.garasi.app.GarasiApp
import com.garasi.app.sync.SyncProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-only, for the automated end-to-end test:
 * adb shell am broadcast -n com.garasi.app/.debug.DebugPair --es host 10.0.2.2 --es token CODE
 */
class DebugPair : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as GarasiApp
        val host = intent.getStringExtra("host") ?: return
        val token = intent.getStringExtra("token") ?: return
        app.sync.pair(SyncProtocol.Pairing(listOf(host), SyncProtocol.DEFAULT_PORT, token))
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { android.util.Log.i("GarasiSync", "result=" + app.sync.sync()) } finally { pending.finish() }
        }
    }
}
