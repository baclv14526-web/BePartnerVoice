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

    private val commandButtonMap: Map<VoiceCommand, List<String>> = mapOf(
        VoiceCommand.DA_DEN       to listOf("Đã đến điểm đón", "ĐÃ ĐẾN ĐIỂM ĐÓN", "ĐA DEN DIEM DON", "Đã đến", "Arrived", "da den"),
        VoiceCommand.BAT_DAU      to listOf("Bắt đầu chuyến đi", "BẮT ĐẦU CHUYẾN ĐI", "Bắt đầu chuyến", "Bắt đầu", "Start trip", "Start", "bat dau"),
        VoiceCommand.TRA_KHACH    to listOf("Trả khách", "TRẢ KHÁCH", "KẾT THÚC", "Hoàn thành", "Kết thúc", "Complete", "End trip", "Complete trip", "tra khach"),
        VoiceCommand.CHAP_NHAN    to listOf("Chấp nhận", "CHẤP NHẬN", "NHẬN CHUYẾN", "Nhận chuyến", "Accept", "OK", "Đồng ý", "chap nhan"),
        VoiceCommand.TU_CHOI      to listOf("Từ chối", "TỪ CHỐI", "Bỏ qua", "Decline", "Skip", "tu choi"),
        VoiceCommand.BAO_CAO      to listOf("Báo cáo", "Report", "Vấn đề", "bao cao"),
        VoiceCommand.ONLINE       to listOf("Bắt đầu nhận chuyến", "BẮT ĐẦU NHẬN CHUYẾN", "Online", "Go online", "Sẵn sàng"),
        VoiceCommand.OFFLINE      to listOf("Dừng nhận chuyến", "DỪNG NHẬN CHUYẾN", "Offline",  "Go offline", "Nghỉ", "Kết thúc"),
        VoiceCommand.BAT_NHAN_CUOC to listOf("Bật/Tắt", "BẬT/TẮT", "Bắt đầu nhận", "Dừng nhận", "Go", "Toggle", "bat nhan cuoc"),
        VoiceCommand.DEN_DIEM_HANG to listOf("Đã đến điểm nhận hàng", "Đã đến điểm lấy hàng", "Arrived at pickup", "Lấy hàng", "Nhận hàng", "den diem hang"),
        VoiceCommand.DA_NHAN_HANG  to listOf("Đã nhận hàng", "Đã lấy hàng", "Picked up", "Nhận hàng thành công", "da nhan hang"),
        VoiceCommand.CHUP_ANH      to listOf("Chụp ảnh", "Lưu ảnh", "Take photo", "Chụp ảnh xác nhận", "Camera", "chup anh"),
        VoiceCommand.TRA_HANG      to listOf("Trả hàng", "TRẢ HÀNG", "Giao hàng", "Hoàn thành giao hàng", "Delivered", "Complete delivery", "tra hang"),
        VoiceCommand.NGUNG_NHAN    to listOf("Ngừng nhận chuyến", "Không nhận", "Pause", "Tạm ngừng", "ngung nhan"),
        VoiceCommand.XEM_SO_DU     to listOf("Xem số dư", "Số dư", "Ví", "Wallet", "Thu nhập", "Earnings", "so du", "xem so du"),
        // BAT_MICRO / TAT_MICRO xử lý nội bộ, không nhấn nút BeBike
        VoiceCommand.BAT_MICRO    to listOf(),
        VoiceCommand.TAT_MICRO    to listOf(),

        // ── Điều hướng tab – tìm theo text/contentDescription/resourceId ──
        VoiceCommand.TRANG_CHU       to listOf(
            "Trang chủ", "Home", "Trang Chủ", "trang-chu", "home_tab"
        ),
        VoiceCommand.THU_NHAP        to listOf(
            "Thu nhập", "Doanh thu", "Earnings", "Income",
            "Thu Nhập", "thu-nhap", "earnings_tab"
        ),
        VoiceCommand.DICH_VU         to listOf(
            "Dịch vụ", "Services", "Service", "Dich Vu",
            "dich-vu", "service_tab"
        ),
        VoiceCommand.HOP_THU         to listOf(
            "Hộp thư", "Inbox", "Tin nhắn", "Thông báo",
            "Hộp Thư", "hop-thu", "inbox_tab", "message_tab"
        ),
        VoiceCommand.TOI             to listOf(
            "Tôi", "Tài khoản", "Hồ sơ", "Profile",
            "Account", "Me", "toi", "profile_tab", "account_tab"
        ),
        VoiceCommand.LICH_SU         to listOf(
            "Lịch sử", "History", "Lịch Sử",
            "lich-su", "history_tab", "trip_history"
        ),
        VoiceCommand.TI_LE_HOAT_DONG to listOf(
            "Tỉ lệ hoạt động", "Tỷ lệ", "Hiệu suất",
            "Performance", "Rate", "Activity rate",
            "ti-le", "performance_tab", "activity_tab"
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
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        instance = null
        runCatching { unregisterReceiver(commandReceiver) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // ── Thực thi lệnh giọng nói ───────────────────────────────────
    fun executeVoiceCommand(command: VoiceCommand) {
        // BAT_MICRO / TAT_MICRO: điều khiển micro app, không nhấn nút BeBike
        if (command == VoiceCommand.BAT_MICRO) {
            sendBroadcast(Intent(VoiceListenerService.ACTION_START))
            return
        }
        if (command == VoiceCommand.TAT_MICRO) {
            sendBroadcast(Intent(VoiceListenerService.ACTION_STOP))
            return
        }

        val labels = commandButtonMap[command]
        if (labels.isNullOrEmpty()) return

        handler.post {
            val root = rootInActiveWindow ?: return@post
            val node = findButton(root, labels)
            if (node != null) {
                val btnLabel = node.text?.toString()
                    ?: node.contentDescription?.toString()
                    ?: "?"
                clickNode(node)
                VibrationHelper.vibrate(this, VibrationHelper.PATTERN_SUCCESS)
                broadcastResult(command, true, btnLabel)
                Log.i(TAG, "Clicked [$btnLabel] for $command")
            } else {
                VibrationHelper.vibrate(this, VibrationHelper.PATTERN_FAIL)
                broadcastResult(command, false, "")
                Log.w(TAG, "Button not found for $command | labels=$labels")
                // Retry sau 800ms
                handler.postDelayed({
                    rootInActiveWindow?.let { r ->
                        findButton(r, labels)?.let { n ->
                            val lbl = n.text?.toString()
                                ?: n.contentDescription?.toString()
                                ?: "?"
                            clickNode(n)
                            VibrationHelper.vibrate(this, VibrationHelper.PATTERN_SUCCESS)
                            broadcastResult(command, true, lbl)
                        }
                    }
                }, 800)
            }
        }
    }

    // ── Dump view hierarchy để debug ─────────────────────────────
    fun dumpCurrentWindow() {
        handler.post {
            val root = rootInActiveWindow
            if (root == null) {
                sendBroadcast(Intent("com.bepartner.voiceassist.VIEW_DUMP").apply {
                    putExtra("dump", "rootInActiveWindow = null\nMở BeBike rồi thử lại.")
                })
                return@post
            }
            val sb = StringBuilder()
            sb.append("Package: ").append(root.packageName).append("\n")
            sb.append("─────────────────────────────\n")
            dumpNode(root, sb, 0)
            sendBroadcast(Intent("com.bepartner.voiceassist.VIEW_DUMP").apply {
                putExtra("dump", sb.toString())
            })
        }
    }

    private fun dumpNode(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 12) return
        val indent = "  ".repeat(depth)
        val text   = node.text?.toString()?.take(60) ?: ""
        val desc   = node.contentDescription?.toString()?.take(60) ?: ""
        val resId  = node.viewIdResourceName?.substringAfterLast("/") ?: ""
        val flags  = buildString {
            if (node.isClickable)     append("CLICKABLE ")
            if (!node.isEnabled)      append("DISABLED ")
            if (!node.isVisibleToUser) append("HIDDEN ")
        }.trim()

        if (text.isNotEmpty() || desc.isNotEmpty() || node.isClickable) {
            sb.append(indent).append("[").append(flags).append("]\n")
            // Dùng + thay vì string template lồng để tránh lỗi escape
            if (text.isNotEmpty())  sb.append(indent).append("  text=").append(text).append("\n")
            if (desc.isNotEmpty())  sb.append(indent).append("  desc=").append(desc).append("\n")
            if (resId.isNotEmpty()) sb.append(indent).append("  id=").append(resId).append("\n")
            sb.append("\n")
        }
        for (i in 0 until node.childCount) {
            dumpNode(node.getChild(i), sb, depth + 1)
        }
    }

    // ── Tìm nút trên màn hình ─────────────────────────────────────
    private fun findButton(root: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        // Cách 1: tìm chính xác theo text
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (n in nodes) {
                if (isClickable(n)) return n
                clickableAncestor(n)?.let { return it }
            }
        }
        // Cách 2: duyệt cây, khớp một phần
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
        var cur = node?.parent
        var depth = 0
        while (cur != null && depth < 5) {
            if (isClickable(cur)) return cur
            cur = cur.parent
            depth++
        }
        return null
    }

    private fun isClickable(n: AccessibilityNodeInfo) =
        n.isClickable && n.isEnabled && n.isVisibleToUser

    // ── Thực hiện click ──────────────────────────────────────────
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

    private fun broadcastResult(command: VoiceCommand, success: Boolean, buttonText: String) {
        sendBroadcast(Intent("com.bepartner.voiceassist.COMMAND_RESULT").apply {
            putExtra("command", command.name)
            putExtra("success", success)
            putExtra("button_found", buttonText)
        })
    }
}
