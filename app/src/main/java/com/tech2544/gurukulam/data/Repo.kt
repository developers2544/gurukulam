package com.tech2544.gurukulam.data

import com.tech2544.gurukulam.BuildConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLEncoder

/** Backend se saari baatein yahin se hoti hain. Screens seedha network nahi chhoote. */
class Repo(val supa: Supa) {

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun banners(): List<Banner> =
        supa.list("/banners?select=*&active=eq.true&order=sort.asc")

    suspend fun tiles(): List<Tile> =
        supa.list("/grid_tiles?select=*&active=eq.true&order=sort.asc")

    suspend fun content(kind: String?, q: String? = null, limit: Int = 200): List<Content> {
        val sb = StringBuilder("/content?select=*&published=eq.true")
        if (!kind.isNullOrBlank() && kind != "all") sb.append("&kind=eq.").append(enc(kind))
        val clean = q?.replace(Regex("[,()*%]"), " ")?.trim()
        if (!clean.isNullOrBlank()) {
            sb.append("&or=(title.ilike.*").append(enc(clean)).append("*,subtitle.ilike.*").append(enc(clean)).append("*)")
        }
        sb.append("&order=sort.asc,created_at.desc&limit=").append(limit)
        return supa.list(sb.toString())
    }

    suspend fun contentById(id: String): Content? =
        supa.list<Content>("/content?select=*&id=eq.${enc(id)}&limit=1").firstOrNull()

    suspend fun plans(): List<Plan> =
        supa.list("/plans?select=*&active=eq.true&order=sort.asc")

    suspend fun profile(): Profile? {
        val uid = supa.userId ?: return null
        return supa.list<Profile>("/profiles?select=*&id=eq.$uid&limit=1").firstOrNull()
    }

    suspend fun entitlement(): Entitlement? {
        val uid = supa.userId ?: return null
        return supa.list<Entitlement>("/entitlements?select=*&user_id=eq.$uid&limit=1").firstOrNull()
    }

    suspend fun wallet(): Wallet {
        val uid = supa.userId ?: return Wallet()
        return supa.list<Wallet>("/wallets?select=*&user_id=eq.$uid&limit=1").firstOrNull() ?: Wallet()
    }

    suspend fun chatOpen(): Boolean =
        supa.rpc("chat_is_open").trim().equals("true", ignoreCase = true)

    suspend fun chat(room: String): List<ChatMsg> =
        supa.list<ChatMsg>("/chat_messages?select=*&room=eq.${enc(room)}&deleted=eq.false&order=id.desc&limit=60").reversed()

    suspend fun sendChat(room: String, body: String, visibility: String) {
        supa.rpc("send_chat", buildJsonObject {
            put("p_room", room); put("p_body", body); put("p_visibility", visibility)
        })
    }

    suspend fun sendSuper(room: String, body: String) {
        supa.rpc("send_super_chat", buildJsonObject { put("p_room", room); put("p_body", body) })
    }

    suspend fun reportMessage(id: Long) {
        supa.rpc("report_message", buildJsonObject { put("p_id", id); put("p_reason", "user_report") })
    }

    suspend fun activePoll(): Poll? =
        supa.list<Poll>("/polls?select=id,question,poll_options(id,label,votes)&active=eq.true&order=id.desc&limit=1").firstOrNull()

    suspend fun vote(optionId: Long) {
        supa.rpc("vote_poll", buildJsonObject { put("p_option", optionId) })
    }

    suspend fun markPlanShown() { supa.rpc("mark_plan_shown") }

    suspend fun toggleLike(contentId: String): Pair<Int, Boolean> {
        val o = supa.json.parseToJsonElement(
            supa.rpc("toggle_like", buildJsonObject { put("p_content", contentId) })
        ).jsonObject
        return o["count"]!!.jsonPrimitive.int to o["liked"]!!.jsonPrimitive.boolean
    }

    suspend fun myLikes(): Set<String> =
        supa.list<LikeRow>("/content_likes?select=content_id&limit=1000").map { it.contentId }.toSet()

    suspend fun saveFcm(token: String, hour: Int) {
        supa.rpc("save_fcm_token", buildJsonObject { put("p_token", token); put("p_hour", hour) })
    }

    suspend fun updateProfile(lang: String?, hour: Int?) {
        supa.rpc("update_profile", buildJsonObject {
            if (lang != null) put("p_lang", lang)
            if (hour != null) put("p_hour", hour)
        })
    }

    suspend fun deleteAccount() { supa.rpc("delete_my_account") }

    suspend fun verifyPurchase(productId: String, purchaseToken: String) {
        supa.function("verify-purchase", buildJsonObject {
            put("productId", productId); put("purchaseToken", purchaseToken)
        })
    }

    /** Free = seedha CDN link. Premium = server plan check karke chhota-sa signed link deta hai. */
    suspend fun mediaUrl(c: Content): String {
        val key = c.mediaKey ?: throw IllegalStateException("No media")
        if (key.startsWith("http")) return key
        if (!c.isPremium) return BuildConfig.CDN_BASE.trimEnd('/') + "/" + key.trimStart('/')
        try {
            val r = supa.function("get-media-url", buildJsonObject { put("content_id", c.id) })
            return supa.json.parseToJsonElement(r).jsonObject["url"]!!.jsonPrimitive.content
        } catch (e: ApiException) {
            if (e.code == 403) throw PremiumRequired()
            throw e
        }
    }
}
