package com.garasi.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.garasi.app.GarasiApp
import com.garasi.app.R
import com.garasi.app.sync.SyncManager
import com.garasi.app.sync.SyncProtocol
import com.garasi.app.ui.BigButton
import com.garasi.app.ui.SectionCard
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun syncStateText(s: SyncManager.State): String = when (s) {
    SyncManager.State.Idle -> stringResource(R.string.sync_idle)
    SyncManager.State.Syncing -> stringResource(R.string.sync_syncing)
    is SyncManager.State.Done -> stringResource(R.string.sync_done, s.items)
    is SyncManager.State.Failed -> stringResource(
        when (s.reason) {
            SyncManager.Reason.NOT_PAIRED -> R.string.sync_not_paired
            SyncManager.Reason.UNREACHABLE -> R.string.sync_unreachable
            SyncManager.Reason.BAD_CODE -> R.string.sync_bad_code
            SyncManager.Reason.ERROR -> R.string.sync_error
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(nav: NavController) {
    val app = LocalContext.current.applicationContext as GarasiApp
    val sync = app.sync
    val pairing by sync.pairing.collectAsStateWithLifecycle()
    val state by sync.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var manual by remember { mutableStateOf(false) }
    var confirmUnpair by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            title = { Text(stringResource(R.string.sync_title)) },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(stringResource(R.string.sync_how_title), icon = Icons.Filled.Computer) {
                Text(stringResource(R.string.sync_how_body), style = MaterialTheme.typography.bodyMedium)
            }
            val p = pairing
            if (p == null) {
                SectionCard(stringResource(R.string.sync_pair_title), icon = Icons.Filled.QrCodeScanner) {
                    BigButton(stringResource(R.string.sync_scan), Icons.Filled.QrCodeScanner, { nav.navigate("scan") }, Modifier.fillMaxWidth())
                    BigButton(stringResource(R.string.sync_manual), null, { manual = true }, Modifier.fillMaxWidth(), outlined = true)
                }
            } else {
                SectionCard(stringResource(R.string.sync_paired_title), icon = Icons.Filled.Sync) {
                    Text(stringResource(R.string.sync_paired_with, p.hosts.joinToString(", "), p.port), style = MaterialTheme.typography.bodyMedium)
                    val last = sync.lastSync
                    Text(
                        if (last > 0) stringResource(R.string.sync_last, DateUtils.getRelativeTimeSpanString(last).toString())
                        else stringResource(R.string.sync_never),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (state is SyncManager.State.Syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(syncStateText(state), style = MaterialTheme.typography.bodyMedium,
                        color = if (state is SyncManager.State.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    BigButton(stringResource(R.string.sync_now), Icons.Filled.Sync, { scope.launch { sync.sync() } },
                        Modifier.fillMaxWidth(), enabled = state !is SyncManager.State.Syncing)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({ nav.navigate("scan") }) { Text(stringResource(R.string.sync_rescan)) }
                        TextButton({ confirmUnpair = true }) { Text(stringResource(R.string.sync_unpair)) }
                    }
                }
            }
            Text(stringResource(R.string.sync_privacy), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (manual) {
        var host by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manual = false },
            title = { Text(stringResource(R.string.sync_manual)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.sync_manual_body))
                    OutlinedTextField(host, { host = it.trim() }, label = { Text(stringResource(R.string.sync_pc_address)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OutlinedTextField(code, { code = it.trim().uppercase() }, label = { Text(stringResource(R.string.sync_code)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                }
            },
            confirmButton = {
                TextButton({
                    val h = host.substringBefore(':')
                    val port = host.substringAfter(':', "").toIntOrNull() ?: SyncProtocol.DEFAULT_PORT
                    if (h.isNotBlank() && code.length >= 6) {
                        sync.pair(SyncProtocol.Pairing(listOf(h), port, code.replace("-", "")))
                        manual = false
                        scope.launch { sync.sync() }
                    }
                }) { Text(stringResource(R.string.sync_connect)) }
            },
            dismissButton = { TextButton({ manual = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmUnpair) AlertDialog(
        onDismissRequest = { confirmUnpair = false },
        title = { Text(stringResource(R.string.sync_unpair_q)) },
        text = { Text(stringResource(R.string.sync_unpair_body)) },
        confirmButton = { TextButton({ sync.unpair(); confirmUnpair = false }) { Text(stringResource(R.string.sync_unpair)) } },
        dismissButton = { TextButton({ confirmUnpair = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Scans the pairing QR code shown by the Garasi PC app (CameraX + on-device ML Kit). */
@Composable
fun ScanQrScreen(nav: NavController) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as GarasiApp
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    var done by remember { mutableStateOf(false) }

    if (!granted) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.camera_permission_needed))
            TextButton({ nav.popBackStack() }) { Text(stringResource(R.string.back)) }
        }
        return
    }
    val previewView = remember { PreviewView(ctx) }
    DisposableEffect(lifecycle) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient()
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null || done) { proxy.close(); return@setAnalyzer }
                scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { codes ->
                        val pairing = codes.firstNotNullOfOrNull { c -> c.rawValue?.let { SyncProtocol.parsePairing(it) } }
                        if (pairing != null && !done) {
                            done = true
                            app.sync.pair(pairing)
                            scope.launch { app.sync.sync() }
                            nav.popBackStack()
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            runCatching {
                provider?.unbindAll()
                provider?.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { provider?.unbindAll(); scanner.close(); executor.shutdown() }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView({ previewView }, Modifier.fillMaxSize())
        Box(Modifier.align(Alignment.Center).fillMaxWidth(0.7f).aspectRatio(1f).border(3.dp, Color.White, MaterialTheme.shapes.large))
        Text(
            stringResource(R.string.sync_scan_tip), color = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(40.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        IconButton({ nav.popBackStack() }, Modifier.padding(top = 40.dp, start = 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
        }
    }
}
