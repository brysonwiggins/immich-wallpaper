package com.wiggyserver.immichwallpaper.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wiggyserver.immichwallpaper.AppLog

/** Restarts the listener after reboot or an app update if the user had it on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val wanted = SwapService.isWanted(context)
        AppLog.log("BOOT", "action=${intent.action} wanted=$wanted")
        if (wanted) SwapService.start(context, "boot:${intent.action}")
    }
}
