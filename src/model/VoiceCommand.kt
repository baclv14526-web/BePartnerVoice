package com.bepartner.voiceassist.model

enum class VoiceCommand(
    val displayName: String,
    val icon: String
) {
    // ── Chuyến xe ──────────────────────────────────────────────
    DA_DEN         ("Đã đến điểm đón",          "📍"),
    BAT_DAU        ("Bắt đầu chuyến đi",         "🚀"),
    TRA_KHACH      ("Trả khách / Hoàn thành",    "✅"),
    CHAP_NHAN      ("Chấp nhận chuyến",          "👍"),
    TU_CHOI        ("Từ chối chuyến",            "❌"),
    BAO_CAO        ("Báo cáo vấn đề",            "⚠️"),

    // ── Online / Offline ───────────────────────────────────────
    ONLINE         ("Bắt đầu nhận chuyến",       "🟢"),
    OFFLINE        ("Dừng nhận chuyến",           "🔴"),
    BAT_NHAN_CUOC  ("Bật/Tắt nhận cuốc",          "🔁"),
    BAT_MICRO      ("Bật micro",                   "🎤"),
    TAT_MICRO      ("Tắt micro",                   "🔇"),

    // ── Giao hàng (mới) ────────────────────────────────────────
    DEN_DIEM_HANG  ("Đã đến điểm nhận hàng",     "📦"),
    DA_NHAN_HANG   ("Đã nhận hàng",              "🛍️"),
    CHUP_ANH       ("Chụp ảnh nhận hàng",        "📷"),
    TRA_HANG       ("Trả hàng",                  "↩️"),
    NGUNG_NHAN     ("Ngừng nhận chuyến",          "🛑"),
}
