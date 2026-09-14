package calino.malinov.ski.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

enum class AiProvider(val label: String, val defaultUrl: String) {
    Anthropic("Anthropic", "https://api.anthropic.com/v1"),
    OpenAi("OpenAI", "https://api.openai.com/v1"),
    Custom("Custom", "https://api.xiaomimimo.com/v1"),
}

data class AiVisionSettings(
    val provider: AiProvider = AiProvider.Custom,
    val baseUrl: String = AiProvider.Custom.defaultUrl,
    val model: String = "mimo-v2.5",
    val hasApiKey: Boolean = false,
    val lastVerifiedAt: Long? = null,
    val visionCapable: Boolean? = null,
)

data class AiEventCandidate(
    val title: String? = null,
    val location: String? = null,
    val description: String? = null,
    val start: LocalDateTime? = null,
    val end: LocalDateTime? = null,
    val allDay: Boolean = false,
    val confidence: String? = null,
    val kind: String = "event",
) {
    fun isUsable() = !title.isNullOrBlank() || start != null || !location.isNullOrBlank() || !description.isNullOrBlank()
}

class AiVisionSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("calino_ai_vision", Context.MODE_PRIVATE)

    fun load(): AiVisionSettings {
        val provider = runCatching { AiProvider.valueOf(prefs.getString("provider", null) ?: "") }
            .getOrDefault(AiProvider.Custom)
        return AiVisionSettings(
            provider = provider,
            baseUrl = prefs.getString("base_url", null) ?: provider.defaultUrl,
            model = prefs.getString("model", null) ?: if (provider == AiProvider.Custom) "mimo-v2.5" else "",
            hasApiKey = prefs.contains("api_key"),
            lastVerifiedAt = prefs.getLong("verified_at", 0L).takeIf { it > 0 },
            visionCapable = if (prefs.contains("vision_capable")) prefs.getBoolean("vision_capable", false) else null,
        )
    }

    fun saveConfig(provider: AiProvider, baseUrl: String, model: String) {
        prefs.edit().putString("provider", provider.name).putString("base_url", baseUrl.trim())
            .putString("model", model.trim()).apply()
    }

    fun saveApiKey(value: String) {
        if (value.isBlank()) return
        prefs.edit().putString("api_key", encrypt(value.trim())).apply()
    }

    fun apiKey(): String? = prefs.getString("api_key", null)?.let(::decrypt)
    fun clearApiKey() = prefs.edit().remove("api_key").remove("verified_at").remove("vision_capable").apply()
    fun saveVerification(capable: Boolean) = prefs.edit().putLong("verified_at", System.currentTimeMillis())
        .putBoolean("vision_capable", capable).apply()

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val parts = value.split(":", limit = 2)
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))
    }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(KeyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KeyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private companion object { const val KeyAlias = "calino_ai_vision_key"; const val Transformation = "AES/GCM/NoPadding" }
}

data class AiConnectionResult(val ok: Boolean, val visionCapable: Boolean? = null, val message: String)

class AiVisionClient(private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(90)).build()) {
    suspend fun listModels(settings: AiVisionSettings, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val body = request(settings, apiKey, "/models")
        val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
        buildList {
            for (i in 0 until data.length()) data.optJSONObject(i)?.optString("id")?.takeIf { id ->
                listOf("text-embedding", "whisper", "tts", "dall-e", "omni-moderation", "text-moderation").none(id::startsWith)
            }?.let(::add)
        }
    }

    suspend fun test(settings: AiVisionSettings, apiKey: String): AiConnectionResult = withContext(Dispatchers.IO) {
        runCatching {
            listModels(settings, apiKey)
            val reply = sendVision(settings, apiKey, TestImage, "image/png", null,
                "Reply with exactly one word: YES if you can see an image in this message, or NO if you cannot.", 2000)
            val yes = Regex("\\byes\\b", RegexOption.IGNORE_CASE).find(reply)
            val no = Regex("\\bno\\b", RegexOption.IGNORE_CASE).find(reply)
            val capable = yes != null && (no == null || yes.range.first < no.range.first)
            AiConnectionResult(true, capable, if (capable) "Connection OK — vision-capable" else "Connected, but this model may not support images")
        }.getOrElse { AiConnectionResult(false, message = friendlyError(it)) }
    }

    suspend fun extract(settings: AiVisionSettings, apiKey: String, image: ByteArray, mimeType: String): List<AiEventCandidate> =
        withContext(Dispatchers.IO) {
            val encoded = Base64.encodeToString(resizeImage(image), Base64.NO_WRAP)
            val reply = sendVision(settings, apiKey, encoded, "image/jpeg", extractionPrompt(),
                "Extract the event or task details from this image as a JSON array of candidates, per the system instructions.", 8192)
            parseCandidates(reply)
        }

    private fun request(settings: AiVisionSettings, apiKey: String, path: String, json: JSONObject? = null): String {
        val url = settings.baseUrl.trimEnd('/') + path
        val builder = Request.Builder().url(url).apply {
            if (isAnthropic(settings)) addHeader("x-api-key", apiKey).addHeader("anthropic-version", "2023-06-01")
            else addHeader("Authorization", "Bearer $apiKey")
            if (json != null) post(json.toString().toRequestBody("application/json".toMediaType()))
        }
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403) error("Authentication failed — check your API key and base URL.")
                val detail = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
                error(detail?.takeIf(String::isNotBlank) ?: "Request failed with status ${response.code}")
            }
            return text
        }
    }

    private fun sendVision(s: AiVisionSettings, key: String, image: String, mime: String, system: String?, prompt: String, max: Int): String {
        val body = if (isAnthropic(s)) JSONObject().put("model", s.model).put("max_tokens", max).apply {
            if (system != null) put("system", system)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", JSONArray()
                .put(JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", mime).put("data", image)))
                .put(JSONObject().put("type", "text").put("text", prompt)))))
        } else JSONObject().put("model", s.model).put("max_tokens", max).put("messages", JSONArray().apply {
            if (system != null) put(JSONObject().put("role", "system").put("content", system))
            put(JSONObject().put("role", "user").put("content", JSONArray()
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:$mime;base64,$image")))
                .put(JSONObject().put("type", "text").put("text", prompt))))
        })
        val response = JSONObject(request(s, key, if (isAnthropic(s)) "/messages" else "/chat/completions", body))
        return (if (isAnthropic(s)) response.optJSONArray("content")?.optJSONObject(0)?.optString("text")
            else response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content"))
            ?.takeIf(String::isNotBlank) ?: error("Response did not contain text")
    }

    private fun isAnthropic(s: AiVisionSettings) = s.provider == AiProvider.Anthropic ||
        (s.provider == AiProvider.Custom && runCatching { java.net.URI(s.baseUrl).path.split('/').any { it.equals("anthropic", true) } }.getOrDefault(false))

    internal fun parseCandidates(raw: String): List<AiEventCandidate> {
        val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val value = runCatching { JSONArray(clean) }.getOrElse {
            val a = clean.indexOf('['); val b = clean.lastIndexOf(']')
            if (a >= 0 && b > a) JSONArray(clean.substring(a, b + 1)) else JSONArray().put(JSONObject(clean.substring(clean.indexOf('{'), clean.lastIndexOf('}') + 1)))
        }
        return (0 until minOf(value.length(), 5)).map { i -> value.optJSONObject(i) ?: JSONObject() }.map { o ->
            fun date(name: String) = o.optString(name).takeIf(String::isNotBlank)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            AiEventCandidate(o.optString("title").takeIf(String::isNotBlank), o.optString("location").takeIf(String::isNotBlank),
                o.optString("description").takeIf(String::isNotBlank), date("start"), date("end"), o.optBoolean("allDay"),
                o.optString("confidence").takeIf { it in setOf("low", "medium", "high") }, o.optString("kind").takeIf { it in setOf("event", "task") } ?: "event")
        }.ifEmpty { listOf(AiEventCandidate()) }
    }

    private fun resizeImage(bytes: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3200) sample *= 2
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Could not decode image")
        val scale = minOf(1f, 1600f / maxOf(original.width, original.height))
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true) else original
        return ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it); it.toByteArray() }
    }

    private fun friendlyError(t: Throwable): String = when {
        Regex("401|403|authentication|unauthorized|forbidden", RegexOption.IGNORE_CASE).containsMatchIn(t.message.orEmpty()) -> "Authentication failed — check your API key and base URL."
        else -> t.message ?: "Could not reach the AI provider."
    }
}

private fun extractionPrompt(): String {
    val now = ZonedDateTime.now()
    return """You are an expert at reading event flyers, posters, invitations, to-do lists, and screenshots and extracting structured calendar details.
Current date/time (UTC, ISO-8601): ${now.withZoneSameInstant(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}
User's IANA timezone: ${ZoneId.systemDefault().id}
Respond with STRICT JSON ONLY: an array of up to five objects with optional title, location, description, start, end, allDay, confidence, and kind fields. Use kind task for things to do and event for places to be. Return multiple candidates only for distinct events, list items, or genuine ambiguity. start/end are local YYYY-MM-DDTHH:mm without an offset. Resolve relative dates from the current date. For date-only items use midnight and allDay true. Omit unknown values and do not invent end times. Keep titles and descriptions concise. confidence is low, medium, or high. If no useful details exist return [{}]."""
}

private const val TestImage = "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgEAIAAACsiDHgAAACgElEQVRYW2M0MSkvf/2aYcgCpoF2wKgHBtoBox4YaAeMemCgHTDqAVoY2iwYcZ731enTHR0iIqf/dfwXYRBL5P/HbDIEPMBuyHqJcau9spYPuwVU6CxDJ4OI6249HrZjQ8ADtl2aVmwCnNPZchk+HVt589VvR4i4a7v+FPZbQ8ADHooGy9mhZs723dv1TeQu54sXf0u1NWResThI6wiZMK8dpB7g+cvBwaRnFa4uxnbgee77e/+qr9g/Yvk9fV/FVcmf3RA1rrv1eNipnJCo5gGHPdpxbImsM5j3M/TuvH8x8uc/iPi+1Zf7f5VD2G4h+nLs7wapB5ATz84HF+AeuMP54sWf0sd5bw3/ialOkNRi3qzwQzSbRW0QeUBwKs9xpt2m3SoPWN3v579q/rsR4mhkNfsqrkjAE1K7/hQ2qmVoKnjA5Z1uM/smpjTG2Qw2O4QvXPj5ClPNvlWXJ8ATUof+VOqVSIyU98jmLMn8KJCvry4/nWUp8boif0yU/NB9x/Z5/J9ESmxnoUSzuC5/O7OnfpR8Bcv2t/O/qP2X2bXm4uOfQrjUa/VJX2eR1P+ioMCy061Dbyr7rTsMz0/9ocQJlHnALcRAjv0twzyGxwwiWyTPeP4onCKzY8fXeFzqdeRkuVlD5zNkM/BDS6RpW3ee+kqRByjKA+6K+vCSZwdS0YkLXOF+3Pt79QurD+n/+qV1hEyY1mr2y1xnlRgAD8hriz5iFlLXlLJhvgMteYhOzbtdL335aQ0NghB9OTaKagYyPeAWoi8Lq5J23r9AMOyRwa6Ki9k/ofWAy249HvajjGcZOxlEyHMJFUqhgQWjHZqBBqMeGGgw6oGBBkPeAwDQbeRxVaIJNwAAAABJRU5ErkJggg=="
