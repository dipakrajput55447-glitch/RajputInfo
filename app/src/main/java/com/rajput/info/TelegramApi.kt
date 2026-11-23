package com.rajput.info

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object TelegramApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    private val base get() = "https://api.telegram.org/bot\${BotConfig.BOT_TOKEN}"
    private val json = "application/json; charset=utf-8".toMediaType()

    fun sendMessage(text: String): Boolean {
        val payload = JSONObject()
            .put("chat_id", BotConfig.CHAT_ID)
            .put("text", text.take(4096))
            .put("parse_mode", "HTML")
            .toString()
        val req = Request.Builder().url("\$base/sendMessage")
            .post(payload.toRequestBody(json)).build()
        return runCatching { client.newCall(req).execute().use { it.isSuccessful } }
            .getOrDefault(false)
    }

    fun sendPhoto(bytes: ByteArray, caption: String = ""): Boolean {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", BotConfig.CHAT_ID.toString())
            .addFormDataPart("caption", caption.take(1024))
            .addFormDataPart("photo", "img.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val req = Request.Builder().url("\$base/sendPhoto").post(body).build()
        return runCatching { client.newCall(req).execute().use { it.isSuccessful } }
            .getOrDefault(false)
    }

    fun getUpdates(offset: Long, timeout: Int = 25): JSONArray? {
        val req = Request.Builder()
            .url("\$base/getUpdates?offset=\$offset&timeout=\$timeout")
            .build()
        return runCatching {
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val body = res.body?.string() ?: return@use null
                JSONObject(body).optJSONArray("result")
            }
        }.getOrNull()
    }
}
