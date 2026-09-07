package com.bepartner.voiceassist.util

import com.bepartner.voiceassist.model.VoiceCommand

/**
 * VoiceCommandParser
 *
 * Maps raw speech-recognition text → VoiceCommand.
 *
 * Recognition results are often imperfect, so we:
 *  1. Normalize (lowercase, strip diacritics optionally)
 *  2. Check keyword lists with partial matching
 *  3. Apply a simple confidence threshold (keyword hit count)
 *
 * Add more trigger phrases here as you discover what Google
 * Speech Recognition returns for your accent / microphone.
 */
object VoiceCommandParser {

    // ──────────────────────────────────────────────
    // Trigger phrase lists per command
    // Each list contains all phrases that should activate that command.
    // Shorter phrases appear later so longer phrases match first.
    // ──────────────────────────────────────────────
    private val triggerPhrases: Map<VoiceCommand, List<String>> = mapOf(

        VoiceCommand.DA_DEN to listOf(
            "đã đến điểm đón",
            "đã đến nơi",
            "đã đến rồi",
            "đến điểm đón",
            "tôi đã đến",
            "arrived",
            "da den roi",
            "da den",
            "den noi",
            "đến rồi"
        ),

        VoiceCommand.BAT_DAU to listOf(
            "bắt đầu chuyến đi",
            "bắt đầu chuyến",
            "bắt đầu đi",
            "khởi hành",
            "đi thôi",
            "start trip",
            "bat dau chuyen di",
            "bat dau chuyen",
            "bat dau di",
            "bat dau"
        ),

        VoiceCommand.TRA_KHACH to listOf(
            "trả khách rồi",
            "trả khách",
            "hoàn thành chuyến",
            "kết thúc chuyến",
            "xong rồi",
            "complete trip",
            "end trip",
            "tra khach roi",
            "tra khach",
            "hoan thanh"
        ),

        VoiceCommand.CHAP_NHAN to listOf(
            "chấp nhận chuyến",
            "nhận chuyến",
            "chấp nhận",
            "đồng ý",
            "ok nhận",
            "accept",
            "chap nhan chuyen",
            "chap nhan",
            "nhan chuyen"
        ),

        VoiceCommand.TU_CHOI to listOf(
            "từ chối chuyến",
            "từ chối",
            "bỏ qua",
            "decline",
            "skip",
            "tu choi chuyen",
            "tu choi",
            "bo qua"
        ),

        VoiceCommand.BAO_CAO to listOf(
            "báo cáo vấn đề",
            "báo cáo sự cố",
            "có vấn đề",
            "report",
            "bao cao"
        ),

        VoiceCommand.ONLINE to listOf(
            "bắt đầu nhận chuyến",
            "online",
            "sẵn sàng",
            "go online",
            "bat dau nhan chuyen",
            "san sang"
        ),

        VoiceCommand.OFFLINE to listOf(
            "dừng nhận chuyến",
            "offline",
            "nghỉ thôi",
            "go offline",
            "nghi thoi",
            "dung nhan chuyen"
        )
    )

    /**
     * Parse a list of speech recognition results into a VoiceCommand.
     *
     * Google returns multiple hypotheses ranked by confidence – we check all of them.
     *
     * @param results list from SpeechRecognizer (index 0 = highest confidence)
     * @return matched VoiceCommand or null if no match found
     */
    fun parse(results: List<String>): VoiceCommand? {
        for (rawText in results) {
            val normalized = normalize(rawText)
            val command = matchCommand(normalized)
            if (command != null) return command
        }
        return null
    }

    private fun matchCommand(normalized: String): VoiceCommand? {
        // Longest-match wins: sort triggers by phrase length descending
        val allCandidates = mutableListOf<Pair<VoiceCommand, String>>()
        for ((cmd, phrases) in triggerPhrases) {
            for (phrase in phrases) {
                allCandidates.add(cmd to phrase)
            }
        }
        allCandidates.sortByDescending { it.second.length }

        for ((cmd, phrase) in allCandidates) {
            if (normalized.contains(normalize(phrase))) {
                return cmd
            }
        }
        return null
    }

    /**
     * Normalize text: lowercase, collapse whitespace.
     * We deliberately keep diacritics so "đã đến" still matches "đã đến".
     * The Latin fallback phrases (da den, bat dau …) handle cases where
     * the STT engine strips diacritics.
     */
    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("\\s+"), " ").trim()
}
