package com.rajput.info

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import java.util.UUID

class DeviceIdentity(context: Context) {
    private val prefs = context.getSharedPreferences("device_id", Context.MODE_PRIVATE)
    private val ctx = context.applicationContext

    fun code(): String {
        var c = prefs.getString("code", null)
        if (c == null) {
            c = UUID.randomUUID().toString().replace("-", "").substring(0, 6).uppercase()
            prefs.edit().putString("code", c).apply()
        }
        return c
    }

    fun name(): String {
        val model = Build.MODEL.replace(" ", "").ifBlank { "Device" }
        return "$model-${code()}"
    }

    fun phoneNumber(): String {
        return runCatching {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("MissingPermission")
            tm.line1Number ?: "—"
        }.getOrDefault("—")
    }
}
