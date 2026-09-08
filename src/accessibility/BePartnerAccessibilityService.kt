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
        VoiceCommand.DA_DEN    to listOf("Đã đến", "Đã đến điểm đón", "Arrived", "da den"),
        VoiceCommand.BAT_DAU   to listOf("Bắt đầu chuyến đi", "Bắt đầu", "Start trip", "bat dau"),
        VoiceCommand.TRA_KHACH to listOf("Trả khách", "Hoàn thành", "Complete", "End trip", "tra khach"),
        VoiceCommand.CHAP_NHAN to listOf("Chấp nhận", "Accept", "Nhận chuyến", "chap nhan"),
        VoiceCommand.TU_CHOI   to listOf("Từ chối", "Decline", "Bỏ qua", "tu choi"),
        VoiceCommand.BAO_CAO   to listOf("Báo cáo", "Report", "bao cao"),
        VoiceCommand.ONLINE    to listOf("Online", "Bắt đầu nhận chuyến", "Go online"),
        VoiceCommand.OFFLINE   to listOf("Offline", "Dừng nhận chuyến", "Go offline")
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
        val labels = commandButtonMap[command] ?: return
        handler.post {
            val root = rootInActiveWindow ?: return@post
            val node = findButton(root, labels)
            if (node != null) {
                clickNode(node)
                VibrationHelper.vibrate(this, VibrationHelper.PATTERN_SUCCESS)
                broadcastResult(command, true)
            } else {
                VibrationHelper.vibrate(this, VibrationHelper.PATTERN_FAIL)
                broadcastResult(command, false)
                handler.postDelayed({
                    rootInActiveWindow?.let { r ->
                        findButton(r, labels)?.let { n ->
                            clickNode(n)
                            broadcastResult(command, true)
                        }
                    }
                }, 800)
            }
        }
    }

    private fun findButton(root: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (n in nodes) {
                if (isClickable(n)) return n
                val ancestor = clickableAncestor(n)
                if (ancestor != null) return ancestor
            }
        }
        return walkTree(root) { node ->
            if (!isClickable(node)) return@walkTree false
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            labels.any { label -> val lc = label.lowercase(); text.contains(lc) || desc.contains(lc) }
        }
    }

    private fun walkTree(node: AccessibilityNodeInfo, pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
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

    private fun isClickable(n: AccessibilityNodeInfo) = n.isClickable && n.isEnabled && n.isVisibleToUser

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
