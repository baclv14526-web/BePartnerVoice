package com.bepartner.voiceassist.util

import com.bepartner.voiceassist.model.VoiceCommand

object VoiceCommandParser {

    private val triggerPhrases: Map<VoiceCommand, List<String>> = mapOf(

        // ── Đã đến điểm đón ────────────────────────────────────
        VoiceCommand.DA_DEN to listOf(
            "đã đến điểm đón",
            "đã đến nơi đón",
            "đã đến rồi",
            "đến điểm đón",
            "tôi đã đến",
            "đến rồi",
            "arrived",
            "da den diem don",
            "da den roi",
            "da den",
            "den noi"
        ),

        // ── Bắt đầu chuyến ─────────────────────────────────────
        VoiceCommand.BAT_DAU to listOf(
            "bắt đầu chuyến đi",
            "bắt đầu chuyến",
            "bắt đầu đi",
            "khởi hành",
            "đi thôi",
            "start trip",
            "bat dau chuyen di",
            "bat dau chuyen",
            "bat dau di"
            // "bat dau" đặt sau để tránh nhầm với DEN_DIEM_HANG
        ),

        // ── Trả khách ──────────────────────────────────────────
        VoiceCommand.TRA_KHACH to listOf(
            "trả khách rồi",
            "trả khách xong",
            "trả khách",
            "hoàn thành chuyến",
            "kết thúc chuyến",
            "xong rồi",
            "complete trip",
            "end trip",
            "tra khach roi",
            "tra khach xong",
            "tra khach",
            "hoan thanh"
        ),

        // ── Chấp nhận ──────────────────────────────────────────
        VoiceCommand.CHAP_NHAN to listOf(
            "chấp nhận chuyến",
            "nhận chuyến này",
            "nhận chuyến",
            "chấp nhận",
            "đồng ý",
            "ok nhận",
            "accept",
            "chap nhan chuyen",
            "nhan chuyen nay",
            "nhan chuyen",
            "chap nhan"
        ),

        // ── Từ chối ────────────────────────────────────────────
        VoiceCommand.TU_CHOI to listOf(
            "từ chối chuyến",
            "từ chối",
            "bỏ qua chuyến",
            "bỏ qua",
            "decline",
            "skip",
            "tu choi chuyen",
            "tu choi",
            "bo qua chuyen",
            "bo qua"
        ),

        // ── Báo cáo ────────────────────────────────────────────
        VoiceCommand.BAO_CAO to listOf(
            "báo cáo vấn đề",
            "báo cáo sự cố",
            "có vấn đề",
            "report",
            "bao cao van de",
            "bao cao su co",
            "bao cao"
        ),

        // ── Online ─────────────────────────────────────────────
        VoiceCommand.ONLINE to listOf(
            "bắt đầu nhận chuyến",
            "vào ca",
            "online",
            "sẵn sàng",
            "go online",
            "bat dau nhan chuyen",
            "vao ca",
            "san sang"
        ),

        // ── Offline ────────────────────────────────────────────
        VoiceCommand.OFFLINE to listOf(
            "dừng nhận chuyến",
            "kết thúc ca",
            "offline",
            "nghỉ thôi",
            "go offline",
            "dung nhan chuyen",
            "ket thuc ca",
            "nghi thoi"
        ),

        // ── Bật/Tắt nút nhận cuốc (gạt trên BeBike) ────────────
        VoiceCommand.BAT_NHAN_CUOC to listOf(
            "bật tắt nhận cuốc",
            "bật nhận cuốc",
            "tắt nhận cuốc",
            "bật nút nhận cuốc",
            "tắt nút nhận cuốc",
            "bật gạt",
            "tắt gạt",
            "bật tắt",
            "bat tat nhan cuoc",
            "bat nhan cuoc",
            "tat nhan cuoc",
            "bat gat",
            "tat gat",
            "bat tat"
        ),

        // ── Bật micro của app ────────────────────────────────────
        VoiceCommand.BAT_MICRO to listOf(
            "bật micro",
            "bật microphone",
            "tiếp tục nghe",
            "mở micro",
            "bat micro",
            "mo micro"
        ),

        // ── Tắt micro của app ────────────────────────────────────
        VoiceCommand.TAT_MICRO to listOf(
            "tắt micro",
            "tắt microphone",
            "tạm dừng nghe",
            "đóng micro",
            "tat micro",
            "dong micro"
        ),

        // ── Đã đến điểm nhận hàng (mới) ────────────────────────
        VoiceCommand.DEN_DIEM_HANG to listOf(
            "đã đến điểm nhận hàng",
            "đến điểm lấy hàng",
            "đến chỗ lấy hàng",
            "đến kho hàng",
            "tới điểm nhận hàng",
            "da den diem nhan hang",
            "den diem lay hang",
            "den cho lay hang",
            "toi diem nhan hang"
        ),

        // ── Đã nhận hàng (mới) ─────────────────────────────────
        VoiceCommand.DA_NHAN_HANG to listOf(
            "đã nhận hàng rồi",
            "đã lấy hàng rồi",
            "đã nhận hàng",
            "đã lấy hàng",
            "nhận hàng xong",
            "lấy hàng xong",
            "da nhan hang roi",
            "da lay hang roi",
            "da nhan hang",
            "nhan hang xong",
            "lay hang xong"
        ),

        // ── Chụp ảnh nhận hàng (mới) ───────────────────────────
        VoiceCommand.CHUP_ANH to listOf(
            "chụp ảnh nhận hàng",
            "chụp ảnh giao hàng",
            "chụp ảnh xác nhận",
            "chụp ảnh",
            "chụp hình",
            "take photo",
            "chup anh nhan hang",
            "chup anh giao hang",
            "chup anh xac nhan",
            "chup anh",
            "chup hinh"
        ),

        // ── Trả hàng (mới) ─────────────────────────────────────
        VoiceCommand.TRA_HANG to listOf(
            "trả hàng rồi",
            "giao hàng xong",
            "đã giao hàng",
            "trả hàng",
            "giao hàng",
            "hoàn thành giao hàng",
            "delivered",
            "tra hang roi",
            "giao hang xong",
            "da giao hang",
            "tra hang",
            "giao hang"
        ),

        // ── Xem số dư (mới) ─────────────────────────────────────
        VoiceCommand.XEM_SO_DU to listOf(
            "xem số dư",
            "số dư của tôi",
            "kiểm tra số dư",
            "tôi còn bao nhiêu tiền",
            "còn bao nhiêu tiền",
            "xem tiền",
            "kiểm tra tiền",
            "số dư tài khoản",
            "xem tài khoản",
            "xem du",
            "so du",
            "xem so du",
            "kiem tra so du"
        ),

        // ── Ngừng nhận chuyến (mới) ────────────────────────────
        VoiceCommand.NGUNG_NHAN to listOf(
            "ngừng nhận chuyến",
            "không nhận chuyến nữa",
            "tạm ngừng nhận",
            "dừng lại",
            "ngung nhan chuyen",
            "khong nhan chuyen nua",
            "tam ngung nhan",
            "dung lai"
        )
    )

    fun parse(results: List<String>): VoiceCommand? {
        for (rawText in results) {
            val command = matchCommand(normalize(rawText))
            if (command != null) return command
        }
        return null
    }

    private fun matchCommand(normalized: String): VoiceCommand? {
        // Gom tất cả cặp (command, phrase), ưu tiên phrase dài nhất trước
        // để tránh "trả hàng" match nhầm khi người dùng nói "trả khách"
        val candidates = triggerPhrases.flatMap { (cmd, phrases) ->
            phrases.map { cmd to it }
        }.sortedByDescending { it.second.length }

        for ((cmd, phrase) in candidates) {
            if (normalized.contains(normalize(phrase))) return cmd
        }
        return null
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("\\s+"), " ").trim()
}
