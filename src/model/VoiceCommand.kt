package com.bepartner.voiceassist.model

/**
 * All voice commands supported by BePartner Voice Control.
 *
 * Each enum value maps to:
 *  - a set of trigger phrases (defined in VoiceCommandParser)
 *  - a set of on-screen button labels (defined in BePartnerAccessibilityService)
 */
enum class VoiceCommand(
    /** Human-readable label shown in the overlay UI */
    val displayName: String,
    /** Emoji icon shown alongside the display name */
    val icon: String
) {
    DA_DEN("Đã đến điểm đón", "📍"),
    BAT_DAU("Bắt đầu chuyến đi", "🚀"),
    TRA_KHACH("Trả khách / Hoàn thành", "✅"),
    CHAP_NHAN("Chấp nhận chuyến", "👍"),
    TU_CHOI("Từ chối chuyến", "❌"),
    BAO_CAO("Báo cáo vấn đề", "⚠️"),
    ONLINE("Bắt đầu nhận chuyến", "🟢"),
    OFFLINE("Dừng nhận chuyến", "🔴"),
}
