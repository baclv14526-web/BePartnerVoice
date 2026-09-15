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

        /** Package hiện đang hiển thị trên màn hình, cập nhật mỗi khi có sự kiện. */
        @Volatile var currentForegroundPackage: String? = null
    }

    private val handler = Handler(Looper.getMainLooper())

    // Cache root window - được refresh chủ động ở onAccessibilityEvent
    // thay vì chỉ dựa vào rootInActiveWindow (có thể trả về stale data)
    @Volatile private var cachedRoot: AccessibilityNodeInfo? = null

    private val commandButtonMap: Map<VoiceCommand, List<String>> = mapOf(
        VoiceCommand.DA_DEN       to listOf("Đã đến", "Đã đến điểm đón", "Arrived", "da den"),
        VoiceCommand.BAT_DAU      to listOf("Bắt đầu chuyến đi", "Bắt đầu chuyến", "Bắt đầu", "Start trip", "Start", "bat dau"),
        VoiceCommand.TRA_KHACH    to listOf("Trả khách", "Hoàn thành", "Kết thúc", "Complete", "End trip", "Complete trip", "tra khach"),
        VoiceCommand.CHAP_NHAN    to listOf("Chấp nhận", "Nhận chuyến", "Accept", "OK", "Đồng ý", "chap nhan"),
        VoiceCommand.TU_CHOI      to listOf("Từ chối", "Bỏ qua", "Decline", "Skip", "tu choi"),
        VoiceCommand.BAO_CAO      to listOf("Báo cáo", "Report", "Vấn đề", "bao cao"),
        VoiceCommand.ONLINE       to listOf("Online", "Bắt đầu nhận chuyến", "Go online", "Sẵn sàng", "Vào ca"),
        VoiceCommand.OFFLINE      to listOf("Offline", "Dừng nhận chuyến", "Go offline", "Nghỉ", "Kết thúc ca"),
        VoiceCommand.BAT_NHAN_CUOC to listOf("Bật/Tắt", "Nhận cuốc", "Bắt đầu nhận", "Dừng nhận", "Go", "Toggle", "bat nhan cuoc"),
        VoiceCommand.DEN_DIEM_HANG to listOf("Đã đến điểm nhận hàng", "Đã đến điểm lấy hàng", "Arrived at pickup", "Lấy hàng", "Nhận hàng", "den diem hang"),
        VoiceCommand.DA_NHAN_HANG  to listOf("Đã nhận hàng", "Đã lấy hàng", "Picked up", "Nhận hàng thành công", "da nhan hang"),
        VoiceCommand.CHUP_ANH      to listOf("Chụp ảnh", "Chụp hình", "Take photo", "Chụp ảnh xác nhận", "Camera", "chup anh"),
        VoiceCommand.TRA_HANG      to listOf("Trả hàng", "Giao hàng", "Hoàn thành giao hàng", "Delivered", "Complete delivery", "tra hang"),
        VoiceCommand.NGUNG_NHAN    to listOf("Ngừng nhận chuyến", "Không nhận", "Pause", "Tạm ngừng", "ngung nhan"),
        VoiceCommand.XEM_SO_DU     to listOf("Số dư", "Ví", "Wallet", "Balance", "Tài khoản", "Thu nhập", "Earnings", "so du", "xem so du"),
        VoiceCommand.BAT_MICRO    to listOf(),
        VoiceCommand.TAT_MICRO    to listOf(),
        VoiceCommand.TRANG_CHU       to listOf("Trang chủ", "Home", "Trang Chủ", "trang-chu", "home_tab"),
        VoiceCommand.THU_NHAP        to listOf("Thu nhập", "Doanh thu", "Earnings", "Income", "Thu Nhập", "thu-nhap", "earnings_tab"),
        VoiceCommand.DICH_VU         to listOf("Dịch vụ", "Services", "Service", "Dich Vu", "dich-vu", "service_tab"),
        VoiceCommand.HOP_THU         to listOf("Hộp thư", "Inbox", "Tin nhắn", "Thông báo", "Hộp Thư", "hop-thu", "inbox_tab", "message_tab"),
        VoiceCommand.TOI             to listOf("Tôi", "Tài khoản", "Hồ sơ", "Profile", "Account", "Me", "toi", "profile_tab", "account_tab"),
        VoiceCommand.LICH_SU         to listOf("Lịch sử", "History", "Lịch Sử", "lich-su", "history_tab", "trip_history"),
        VoiceCommand.TI_LE_HOAT_DONG to listOf("Tỉ lệ hoạt động", "Tỷ lệ", "Hiệu suất", "Performance", "Rate", "Activity rate", "ti-le", "performance_tab", "activity_tab")
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
        Log.i(TAG, "✅ Accessibility service CONNECTED")
        broadcastServiceState(true)
    }

    override fun onDestroy() {
        Log.w(TAG, "⚠️ Accessibility service DESTROYED (bị hệ thống tắt hoặc người dùng tắt)")
        broadcastServiceState(false)
        instance = null
        cachedRoot = null
        runCatching { unregisterReceiver(commandReceiver) }
        super.onDestroy()
    }

    // ── QUAN TRỌNG: hàm này TRƯỚC ĐÂY bị bỏ trống {} — nguyên nhân        ──
    // ── chính gây chập chờn. rootInActiveWindow chỉ đáng tin cậy nếu     ──
    // ── ta chủ động cache lại root mỗi khi có sự kiện thay đổi màn hình. ──
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString()
        if (pkg != null) currentForegroundPackage = pkg

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Refresh cache mỗi khi màn hình BeBike đổi nội dung
                // (chuyển tab, chuyến mới xuất hiện, dialog bật lên...)
                cachedRoot = rootInActiveWindow
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "⚠️ Accessibility service INTERRUPTED")
    }

    // ── Thực thi lệnh giọng nói ───────────────────────────────────
    fun executeVoiceCommand(command: VoiceCommand) {
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
            attemptClick(command, labels, attempt = 1)
        }
    }

    /**
     * Thử click với retry nhiều lần thay vì chỉ 1 lần như trước.
     * Máy Realme 2 (RAM thấp) thường load UI chậm hơn máy flagship,
     * nên cần retry dài hơn và nhiều lần hơn.
     */
    private fun attemptClick(command: VoiceCommand, labels: List<String>, attempt: Int) {
        val maxAttempts = 4
        // Luôn lấy root MỚI NHẤT trực tiếp từ hệ thống trước, fallback về cache
        val root = rootInActiveWindow ?: cachedRoot

        if (root == null) {
            Log.w(TAG, "❌ [attempt $attempt] rootInActiveWindow = null. " +
                    "Package hiện tại: $currentForegroundPackage — " +
                    "kiểm tra packageNames trong accessibility_service_config.xml có khớp không.")
            if (attempt < maxAttempts) {
                handler.postDelayed({ attemptClick(command, labels, attempt + 1) }, 600L * attempt)
            } else {
                VibrationHelper.vibrate(this, VibrationHelper.PATTERN_FAIL)
                broadcastResult(command, false, "(không đọc được màn hình — kiểm tra package BeBike)")
            }
            return
        }

        val node = findButton(root, labels)
        if (node != null) {
            val btnLabel = node.text?.toString() ?: node.contentDescription?.toString() ?: "?"
            clickNode(node)
            VibrationHelper.vibrate(this, VibrationHelper.PATTERN_SUCCESS)
            broadcastResult(command, true, btnLabel)
            Log.i(TAG, "✅ [attempt $attempt] Clicked \"$btnLabel\" for $command")
        } else {
            Log.w(TAG, "❌ [attempt $attempt/$maxAttempts] Button not found for $command | labels=$labels")
            if (attempt < maxAttempts) {
                // Giãn cách tăng dần: 600ms, 1200ms, 1800ms — cho UI đủ thời gian load
                handler.postDelayed({ attemptClick(command, labels, attempt + 1) }, 600L * attempt)
            } else {
                VibrationHelper.vibrate(this, VibrationHelper.PATTERN_FAIL)
                broadcastResult(command, false, "")
                Log.e(TAG, "❌ HẾT LƯỢT THỬ cho $command sau $maxAttempts lần. " +
                        "Nhãn nút không khớp — dùng Debug Console DUMP VIEWS để xác nhận tên nút thật.")
            }
        }
    }

    // ── Dump view hierarchy để debug ─────────────────────────────
    fun dumpCurrentWindow() {
        handler.post {
            val root = rootInActiveWindow ?: cachedRoot
            if (root == null) {
                sendBroadcast(Intent("com.bepartner.voiceassist.VIEW_DUMP").apply {
                    putExtra("dump", "rootInActiveWindow = null (cache cũng null)\n" +
                            "Package hiện tại: $currentForegroundPackage\n" +
                            "→ Package này CÓ THỂ không nằm trong accessibility_service_config.xml\n" +
                            "→ Mở BeBike rồi thử lại. Nếu vẫn null, xác nhận đúng package name.")
                })
                return@post
            }
            val sb = StringBuilder()
            sb.append("Package: ").append(root.packageName).append("\n")
            sb.append("Foreground hiện tại: ").append(currentForegroundPackage).append("\n")
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
        for (label in labels) {
            // findAccessibilityNodeInfosByText đôi khi trả về node stale
            // sau khi UI vừa đổi — bọc runCatching để không crash service
            val nodes = runCatching { root.findAccessibilityNodeInfosByText(label) }
                .getOrNull() ?: emptyList()
            for (n in nodes) {
                if (isClickable(n)) return n
                clickableAncestor(n)?.let { return it }
            }
        }
        return runCatching {
            walkTree(root) { node ->
                if (!isClickable(node)) return@walkTree false
                val text = node.text?.toString()?.lowercase() ?: ""
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                labels.any { label ->
                    val lc = label.lowercase()
                    text.contains(lc) || desc.contains(lc)
                }
            }
        }.getOrNull()
    }

    private fun walkTree(
        node: AccessibilityNodeInfo,
        pred: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (pred(node)) return node
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            val result = walkTree(child, pred)
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
        runCatching { n.isClickable && n.isEnabled && n.isVisibleToUser }.getOrDefault(false)

    // ── Thực hiện click ──────────────────────────────────────────
    private fun clickNode(node: AccessibilityNodeInfo) {
        val clicked = runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
        if (!clicked) {
            val bounds = Rect()
            runCatching { node.getBoundsInScreen(bounds) }
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

    private fun broadcastServiceState(connected: Boolean) {
        sendBroadcast(Intent("com.bepartner.voiceassist.SERVICE_STATE").apply {
            putExtra("connected", connected)
        })
    }
}
