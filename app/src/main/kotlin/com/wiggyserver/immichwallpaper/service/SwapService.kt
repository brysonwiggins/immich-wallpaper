package com.wiggyserver.immichwallpaper.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.wiggyserver.immichwallpaper.AppLog
import com.wiggyserver.immichwallpaper.AppInfo
import com.wiggyserver.immichwallpaper.graph
import com.wiggyserver.immichwallpaper.ui.MainActivity
import com.wiggyserver.immichwallpaper.work.Work

/**
 * Foreground service (specialUse) whose runtime-registered receiver hears every screen-off
 * (receivers declared in the manifest never get it). The swap itself runs on the graph's swap thread.
 */
class SwapService : Service() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_OFF) return
            val graph = context.graph
            graph.observeDisplay()
            val queued = graph.queue.size // read first: the swap thread may already have taken one
            val decision = graph.swap.onScreenOff()
            AppLog.log("SCREEN_OFF", "decision=$decision queue=$queued")
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(this, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        AppLog.log("SERVICE", "created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        val started = runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type) }
        started.exceptionOrNull()?.let {
            AppLog.log("SERVICE", "startForeground failed ${it.javaClass.simpleName}: ${it.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        AppLog.log("SERVICE", "started reason=${intent?.action}")
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        AppLog.log("SERVICE", "timeout type=$fgsType")
        stopSelf()
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        AppLog.log("SERVICE", "destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Wallpaper changer", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(com.wiggyserver.immichwallpaper.R.drawable.ic_stat_wallpaper)
            .setContentTitle("${AppInfo.NAME} is on")
            .setContentText("New photo each time the screen turns off")
            .setContentIntent(open)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL = "swap-listener"
        private const val NOTIFICATION_ID = 1
        private const val PREFS = "service"
        private const val WANTED = "wanted"

        fun isWanted(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(WANTED, false)

        fun start(context: Context, reason: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(WANTED, true) }
            Work.schedulePeriodicSync(context)
            runCatching { ContextCompat.startForegroundService(context, Intent(context, SwapService::class.java).setAction(reason)) }
                .onFailure { AppLog.log("SERVICE", "start from $reason failed ${it.javaClass.simpleName}: ${it.message}") }
        }

        fun stop(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(WANTED, false) }
            context.stopService(Intent(context, SwapService::class.java))
        }
    }
}
