package com.tech2544.gurukulam.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Banner(
    val id: String,
    val title: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("target_type") val targetType: String? = null, // content | list | plans
    @SerialName("target_id") val targetId: String? = null,
)

@Serializable
data class Tile(
    val key: String,
    @SerialName("label_en") val labelEn: String,
    @SerialName("label_hi") val labelHi: String,
    val icon: String = "star",
    @SerialName("color_from") val colorFrom: String = "#FF4B55",
    @SerialName("color_to") val colorTo: String = "#F9A03F",
    val route: String = "",
)

@Serializable
data class Content(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String? = null,
    @SerialName("thumb_url") val thumbUrl: String? = null,
    @SerialName("media_key") val mediaKey: String? = null,
    @SerialName("media_type") val mediaType: String = "video", // video | audio | image
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("is_ai") val isAi: Boolean = false,
    val section: String? = null,
    val likes: Int = 0,
)

@Serializable
data class BenefitItem(
    val title: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
data class Plan(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    @SerialName("price_inr") val priceInr: Int,
    @SerialName("duration_days") val durationDays: Int,
    @SerialName("product_id") val productId: String,
    @SerialName("base_plan_prepaid") val basePlanPrepaid: String,
    @SerialName("base_plan_auto") val basePlanAuto: String,
    @SerialName("super_chats") val superChats: Int = 0,
    val frames: Int = 0,
    @SerialName("video_url") val videoUrl: String? = null,
    val benefits: List<BenefitItem> = emptyList(),
)

@Serializable
data class Entitlement(
    @SerialName("plan_id") val planId: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("auto_renew") val autoRenew: Boolean = false,
)

@Serializable
data class Wallet(
    @SerialName("super_chats") val superChats: Int = 0,
    val frames: Int = 0,
)

@Serializable
data class Profile(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("plan_shown") val planShown: Boolean = false,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("notify_hour") val notifyHour: Int = 8,
)

@Serializable
data class ChatMsg(
    val id: Long,
    @SerialName("user_id") val userId: String? = null,
    val room: String,
    val body: String,
    val kind: String = "normal", // normal | super | admin
    val visibility: String = "public",
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class PollOption(val id: Long, val label: String, val votes: Int = 0)

@Serializable
data class Poll(
    val id: Long,
    val question: String,
    @SerialName("poll_options") val options: List<PollOption> = emptyList(),
)

@Serializable
data class ConfigRow(val key: String, val value: JsonElement)

@Serializable
data class LikeRow(@SerialName("content_id") val contentId: String)
