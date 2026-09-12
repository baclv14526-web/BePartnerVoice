package com.bepartner.voiceassist.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.bepartner.voiceassist.model.VoiceCommand
import com.bepartner.voiceassist.service.VoiceListenerService
import com.bepartner.voiceassist.util.VibrationHelper

class BePartnerAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "BePartnerA11y"
        const val ACTION_EXECUTE_COMMAND = "com.bepartner.voiceassist.EXECUTE_COMMAND"
        const val EXTRA_COMMAND_KEY = "command_key"
        var instance: BePartnerAccessibilityService? = null
    }

    private val handler = Handler(Looper.getMainLooper())

    // ── Map lệnh → nhãn nút trên màn hình BeBike ─────────────────
    // Thêm nhãn thực tế từ app BeBike vào đây nếu khác với mặc định
    private val commandButtonMap: Map<VoiceCommand, List<String>> = mapOf(

        // Chuyến xe
        VoiceCommand.DA_DEN to listOf(
            "Đã đến", "Đã đến điểm đón", "Arrived", "I've arrived",
            "da den"
        ),
        VoiceCommand.BAT_DAU to listOf(
            "Bắt đầu chuyến đi", "Bắt đầu chuyến", "Bắt đầu",
            "Start trip", "Start", "bat dau"
        ),
        VoiceCommand.TRA_KHACH to listOf(
            "Trả khách", "Hoàn thành", "Kết thúc", "Complete",
            "End trip", "Complete trip", "tra khach"
        ),
        VoiceCommand.CHAP_NHAN to listOf(
            "Chấp nhận", "Nhận chuyến", "Accept", "OK", "Đồng ý",
            "chap nhan"
        ),
        VoiceCommand.TU_CHOI to listOf(
            "Từ chối", "Bỏ qua", "Decline", "Skip", "tu choi"
        ),
        VoiceCommand.BAO_CAO to listOf(
            "Báo cáo", "Report", "Vấn đề", "bao cao"
        ),

        // Online / Offline
        VoiceCommand.ONLINE to listOf(
            "Online", "Bắt đầu nhận chuyến", "Go online", "Sẵn sàng",
            "Vào ca"
        ),
        VoiceCommand.OFFLINE to listOf(
            "Offline", "Dừng nhận chuyến", "Go offline", "Nghỉ",
            "Kết thúc ca"
        ),

        // Bật/Tắt nút gạt nhận cuốc trên BeBike
        VoiceCommand.BAT_NHAN_CUOC to listOf(
            "Bật/Tắt", "Nhận cuốc", "Bắt đầu nhận", "Dừng nhận",
            "Go", "Toggle", "bat nhan cuoc"
        ),

        // Bật/Tắt micro: xử lý nội bộ, không nhấn nút BeBike
        VoiceCommand.BAT_MICRO to listOf(),
        VoiceCommand.TAT_MICRO to listOf(),

        // Giao hàng
        VoiceCommand.DEN_DIEM_HANG to listOf(
            "Đã đến điểm nhận hàng", "Đã đến điểm lấy hàng",
            "Arrived at pickup", "Lấy hàng", "Nhận hàng",
            "den diem hang"
        ),
        VoiceCommand.DA_NHAN_HANG to listOf(
            "Đã nhận hàng", "Đã lấy hàng", "Picked up",
            "Nhận hàng thành công", "Lấy hàng thành công",
            "da nhan hang"
        ),
        VoiceCommand.CHUP_ANH to listOf(
            "Chụp ảnh", "Chụp hình", "Take photo",
            "Chụp ảnh xác nhận", "Chụp ảnh giao hàng",
            "Camera", "chup anh"
        ),
        VoiceCommand.TRA_HANG to listOf(
            "Trả hàng", "Giao hàng", "Hoàn thành giao hàng",
            "Delivered", "Complete delivery", "Giao thành công",
            "tra hang"
        ),
        VoiceCommand.NGUNG_NHAN to listOf(
            "Ngừng nhận chuyến", "Không nhận", "Pause",
            "Tạm ngừng", "ngung nhan"
        )
    )

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val key = intent?.getStringExtra(EXTRA_COMMAND_KEY) ?: return
            val cmd = runCatching { VoiceCommand.valueOf(key) }.getOrNull() ?: return
            executeVoiceCommand(cmd)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val filter = IntentFilter(ACTION_EXECUTE_COMMAND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }
        Log.i(TAG, "Accessibility service connected ✓")
    }

    override fun onDestroy() {
        instance = null
        runCatching { unregisterReceiver(commandReceiver) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun executeVoiceCommand(command: VoiceCommand) {
        // BAT_MICRO / TAT_MICRO: điều khiển micro của app, không nhấn nút BeBike
        if (command == VoiceCommand.BAT_MICRO) {
            handler.post {
                sendBroadcast(Intent(VoiceListenerService.ACTION_START))
            }
            return
        }
        if (command == VoiceCommand.TAT_MICRO) {
            handler.post {
                sendBroadcast(Intent(VoiceListenerService.ACTION_STOP))
            }
            return
        }

        val labels = commandButtonMap[command]
        if (labels.isNullOrEmpty()) return

        handler.post {
            val root = rootInActiveWindow ?: return@post
            val node = findButton(root, labels)
            if (node != null) {
                clickNode(node)
                VibrationHelper.vibrate(this, VibrationHelper.PATTERN_SUCCESS)
                broadcastResult(command, true)
                Log.i(TAG, "✅ Clicked '${node.text ?: node.contentDescription}' for $command")
            } else {
                VibrationHelper.vibrate(this, VibrationHelper.PATTERN_FAIL)
                broadcastResult(command, false)
                Log.w(TAG, "❌ Button not found for $command, retrying in 800ms")
                // Retry 1 lần sau 800ms (UI có thể đang load)
                handler.postDelayed({
                    rootInActiveWindow?.let { r ->
                        findButton(r, labels)?.let { n ->
                            clickNode(n)
                            VibrationHelper.vibrate(this, VibrationHelper.PATTERN_SUCCESS)
                            broadcastResult(command, true)
                        }
                    }
                }, 800)
            }
        }
    }

    // ── Tìm nút trên màn hình ─────────────────────────────────────
    private fun findButton(root: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        // Cách 1: tìm theo text chính xác
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (n in nodes) {
                if (isClickable(n)) return n
                clickableAncestor(n)?.let { return it }
            }
        }
        // Cách 2: duyệt toàn bộ cây view, khớp một phần
        return walkTree(root) { node ->
            if (!isClickable(node)) return@walkTree false
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            labels.any { label ->
                val lc = label.lowercase()
                text.contains(lc) || desc.contains(lc)
            }
        }
    }

    private fun walkTree(
        node: AccessibilityNodeInfo,
        pred: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (pred(node)) return node
        for (i in 0 until node.childCount) {
            val result = walkTree(node.getChild(i) ?: continue, pred)
            if (result != null) return result
        }
        return null
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var cur = node?.parent; var depth = 0
        while (cur != null && depth < 5) {
            if (isClickable(cur)) return cur
            cur = cur.parent; depth++
        }
        return null
    }

    private fun isClickable(n: AccessibilityNodeInfo) =
        n.isClickable && n.isEnabled && n.isVisibleToUser

    // ── Click thực thi ─────────────────────────────────────────────
    private fun clickNode(node: AccessibilityNodeInfo) {
        if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) tapGesture(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
    }

    private fun tapGesture(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 100L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun broadcastResult(command: VoiceCommand, success: Boolean) {
        sendBroadcast(Intent("com.bepartner.voiceassist.COMMAND_RESULT").apply {
            putExtra("command", command.name)
            putExtra("success", success)
        })
    }
}
