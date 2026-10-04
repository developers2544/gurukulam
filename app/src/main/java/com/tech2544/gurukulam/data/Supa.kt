package com.tech2544.gurukulam.data

import android.content.Context
import com.tech2544.gurukulam.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : Exception(message)
class PremiumRequired : Exception("Premium required")

/**
 * Supabase se baat karne ka chhota, saaf client (REST).
 * - Sirf anon key aur user ka token app mein hota hai. service_role key kabhi nahi.
 * - Network na ho to pichla saved (cached) jawab dikhata hai.
 */
class Supa(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("gk_auth", Context.MODE_PRIVATE)
    private val cache = ctx.getSharedPreferences("gk_cache", Context.MODE_PRIVATE)
    private val refreshLock = Mutex()

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anon = BuildConfig.SUPABASE_ANON_KEY

    val loggedIn = MutableStateFlow(prefs.getString("refresh", null) != null)
    val userId: String? get() = prefs.getString("uid", null)

    // ---------- Login ----------
    suspend fun signInWithGoogle(idToken: String, rawNonce: String) {
        val body = buildJsonObject {
            put("provider", "google")
            put("id_token", idToken)
            put("nonce", rawNonce)
        }
        val text = call("POST", "/auth/v1/token?grant_type=id_token", body.toString(), authed = false)
        saveSession(text)
    }

    fun signOut() {
        prefs.edit().clear().apply()
        cache.edit().clear().apply()
        loggedIn.value = false
    }

    private fun saveSession(text: String) {
        val o = json.parseToJsonElement(text).jsonObject
        val exp = System.currentTimeMillis() / 1000 + (o["expires_in"]?.jsonPrimitive?.long ?: 3600L)
        prefs.edit()
            .putString("access", o["access_token"]!!.jsonPrimitive.content)
            .putString("refresh", o["refresh_token"]!!.jsonPrimitive.content)
            .putLong("exp", exp)
            .putString("uid", o["user"]!!.jsonObject["id"]!!.jsonPrimitive.content)
            .apply()
        loggedIn.value = true
    }

    private suspend fun token(): String? {
        val acc = prefs.getString("access", null) ?: return null
        val now = System.currentTimeMillis() / 1000
        if (now < prefs.getLong("exp", 0) - 60) return acc
        return refreshLock.withLock {
            // dusre thread ne refresh kar diya ho to wahi lo
            if (System.currentTimeMillis() / 1000 < prefs.getLong("exp", 0) - 60) {
                return@withLock prefs.getString("access", null)
            }
            val rt = prefs.getString("refresh", null) ?: return@withLock null
            try {
                val body = buildJsonObject { put("refresh_token", rt) }
                saveSession(call("POST", "/auth/v1/token?grant_type=refresh_token", body.toString(), authed = false))
                prefs.getString("access", null)
            } catch (e: ApiException) {
                if (e.code == 400 || e.code == 401) signOut()
                throw e
            }
        }
    }

    // ---------- Low level ----------
    private suspend fun call(
        method: String,
        path: String,
        body: String?,
        authed: Boolean = true,
        extra: Map<String, String> = emptyMap(),
    ): String {
        val tk = if (authed) token() else null
        return withContext(Dispatchers.IO) {
            val b = Request.Builder()
                .url(base + path)
                .header("apikey", anon)
                if (tk != null) b.header("Authorization", "Bearer $tk")
            extra.forEach { (k, v) -> b.header(k, v) }
            val rb = (body ?: "").toRequestBody("application/json".toMediaType())
            if (method == "GET") b.get() else b.method(method, rb)
            http.newCall(b.build()).execute().use { r ->
                val t = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw ApiException(r.code, errorText(t, r.code))
                t
            }
        }
    }

    private fun errorText(t: String, code: Int): String = try {
        val o = json.parseToJsonElement(t).jsonObject
        (o["message"] ?: o["msg"] ?: o["error_description"] ?: o["error"])?.jsonPrimitive?.content ?: "Error $code"
    } catch (e: Exception) {
        "Error $code"
    }

    // ---------- Public helpers ----------
    /** GET with offline cache */
    suspend fun get(path: String): String {
        val key = "c" + path.hashCode()
        return try {
            val t = call("GET", "/rest/v1$path", null)
            cache.edit().putString(key, t).apply()
            t
        } catch (e: IOException) {
            cache.getString(key, null) ?: throw e
        }
    }

    suspend inline fun <reified T> list(path: String): List<T> = json.decodeFromString<List<T>>(get(path))

    suspend fun rpc(name: String, args: JsonObject = JsonObject(emptyMap())): String =
        call("POST", "/rest/v1/rpc/$name", args.toString())

    suspend fun function(name: String, args: JsonObject): String =
        call("POST", "/functions/v1/$name", args.toString())
}
