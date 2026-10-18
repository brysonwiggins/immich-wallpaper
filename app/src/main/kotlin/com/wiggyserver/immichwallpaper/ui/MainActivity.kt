package com.wiggyserver.immichwallpaper.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.wiggyserver.immichwallpaper.AppGraph
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import com.wiggyserver.immichwallpaper.AppLog
import com.wiggyserver.immichwallpaper.AppInfo
import com.wiggyserver.immichwallpaper.BuildConfig
import com.wiggyserver.immichwallpaper.support.Support
import com.wiggyserver.immichwallpaper.support.createSupport
import com.wiggyserver.immichwallpaper.graph
import com.wiggyserver.immichwallpaper.service.SwapService
import com.wiggyserver.immichwallpaper.ui.theme.ImmichWallTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class MainActivity : ComponentActivity() {
    // Models live as long as the Activity; it handles fold/rotation itself (manifest configChanges).
    private val scope = MainScope()
    private lateinit var models: Models
    private lateinit var support: Support

    // The "Nearby devices" prompt (Android 17+ home-network access); the callback gets the answer.
    private var onLocalNetworkResult: (Boolean) -> Unit = {}
    private val localNetworkPrompt = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        AppLog.log("PERMISSION", "local network granted=$granted")
        onLocalNetworkResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val g = graph
        models = Models(g, g.api, g.offline, scope, Dispatchers.IO)
        val system = SystemActions(
            setService = ::setService,
            requestBattery = ::requestBatteryExemption,
            shareLog = ::shareLog,
            openUrl = ::openUrl,
            requestLocalNetwork = ::requestLocalNetwork,
            pickWallpaper = ::pickWallpaper,
            support = createSupport(this).also { support = it },
            version = "${runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"} (${BuildConfig.FLAVOR})",
        )
        val photos = ImmichPhotos(g.api)
        setContent {
            ImmichWallTheme {
                CompositionLocalProvider(LocalPhotos provides photos) {
                    Surface { AppRoot(models, system) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        graph.observeDisplay()
    }

    override fun onDestroy() {
        scope.cancel()
        if (::support.isInitialized) support.close()
        super.onDestroy()
    }

    private fun setService(on: Boolean) {
        if (on) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            SwapService.start(this, "ui")
        } else {
            SwapService.stop(this)
        }
    }

    // The direct request is allowed for apps whose core function needs to run after screen-off.
    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }.onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }

    private fun requestLocalNetwork(onResult: (Boolean) -> Unit) {
        onLocalNetworkResult = onResult
        localNetworkPrompt.launch(AppGraph.ACCESS_LOCAL_NETWORK)
    }

    private fun pickWallpaper() {
        runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose a wallpaper")) }
            .onFailure { AppLog.log("OFF", "no wallpaper picker: ${it.javaClass.simpleName}") }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun shareLog() {
        val text = AppLog.lines.value.joinToString("\n")
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "${AppInfo.NAME} log").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, "Share log"))
    }
}
