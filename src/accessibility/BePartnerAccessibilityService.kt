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

/**
 * BePartnerAccessibilityService
 *
 * Core service that:
 * 1. Listens for broadcast intents from VoiceListenerService
 * 2. Searches the current screen for matching buttons
 * 3. Performs a tap gesture on the found button
 *
 * Button matching strategy (in priority order):
 *   a) Exact text match (case-insensitive)
 *   b) Partial text / description match
 *   c) Resource-ID match (fallback for UI that has no visible text)
 */
class BePartnerAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "BePartnerA11y"
        const val ACTION_EXECUTE_COMMAND = "com.bepartner.voiceassist.EXECUTE_COMMAND"
        const val EXTRA_COMMAND_KEY = "command_key"

        // Singleton reference so VoiceListenerService can check if we're running
        var instance: BePartnerAccessibilityService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastWindowContent: AccessibilityNodeInfo? = null

    // ──────────────────────────────────────────────
    // Keyword → Button label mappings (Vietnamese)
    // Add / adjust labels to match actual BeBike UI
    // ──────────────────────────────────────────────
    private val commandButtonMap: Map<VoiceCommand, List<String>> = mapOf(
        VoiceCommand.DA_DEN to listOf(
            "Đã đến", "Đã đến điểm đón", "Arrived", "I've arrived",
            "Tôi đã đến", "Đến nơi", "da den"
        ),
        VoiceCommand.BAT_DAU to listOf(
            "Bắt đầu chuyến đi", "Bắt đầu", "Start trip", "Start",
            "Bắt đầu chuyến", "bat dau"
        ),
        VoiceCommand.TRA_KHACH to listOf(
            "Trả khách", "Hoàn thành", "Complete", "Kết thúc chuyến",
            "Complete trip", "End trip", "Kết thúc", "tra khach"
        ),
        VoiceCommand.CHAP_NHAN to listOf(
            "Chấp nhận", "Accept", "Nhận chuyến", "OK", "Đồng ý",
            "chap nhan", "Xác nhận"
        ),
        VoiceCommand.TU_CHOI to listOf(
            "Từ chối", "Decline", "Bỏ qua", "Skip", "tu choi"
        ),
        VoiceCommand.BAO_CAO to listOf(
            "Báo cáo", "Report", "Vấn đề", "Problem", "bao cao"
        ),
        VoiceCommand.ONLINE to listOf(
            "Online", "Bắt đầu nhận chuyến", "Go online", "Sẵn sàng"
        ),
        VoiceCommand.OFFLINE to listOf(
            "Offline", "Dừng nhận chuyến", "Go offline", "Nghỉ"
        )
    )

    // ──────────────────────────────────────────────
    // BroadcastReceiver – receives commands from VoiceListenerService
    // ──────────────────────────────────────────────
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val commandKey = intent?.getStringExtra(EXTRA_COMMAND_KEY) ?: return
            val command = runCatching { VoiceCommand.valueOf(commandKey) }.getOrNull() ?: return
            Log.d(TAG, "Received command: $command")
            executeVoiceCommand(command)
        }
    }

    // ──────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val filter = IntentFilter(ACTION_EXECUTE_COMMAND)
        registerReceiver(commandReceiver, filter)
        Log.i(TAG, "Accessibility service connected ✓")
    }

    override fun onDestroy() {
        instance = null
        runCatching { unregisterReceiver(commandReceiver) }
        super.onDestroy()
        Log.i(TAG, "Accessibility service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Cache the latest window root for button searches
        event?.source?.let { lastWindowContent = it }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    // ──────────────────────────────────────────────
    // Command execution
    // ──────────────────────────────────────────────
    fun executeVoiceCommand(command: VoiceCommand) {
        val labels = commandButtonMap[command] ?: run {
            Log.w(TAG, "No button labels mapped for command: $command")
            return
        }

        handler.post {
            val root = rootInActiveWindow ?: run {
                Log.e(TAG, "No active window available")
                return@post
            }

            val node = findButtonNode(root, labels)
            if (node != null) {
                performClickOnNode(node)
                Log.i(TAG, "Clicked button for command $command: '${node.text ?: node.contentDescription}'")
                VibrationHelper.vibrate(this, VibrationHelper.PATTERN_SUCCESS)
                broadcastResult(command, success = true)
            } else {
                Log.w(TAG, "Button not found for command: $command. Labels tried: $labels")
                VibrationHelper.vibrate(this, VibrationHelper.PATTERN_FAIL)
                broadcastResult(command, success = false)

                // Retry once after 800 ms (UI may still be loading)
                handler.postDelayed({
                    val retryRoot = rootInActiveWindow ?: return@postDelayed
                    findButtonNode(retryRoot, labels)?.let { retryNode ->
                        performClickOnNode(retryNode)
                        Log.i(TAG, "Retry click succeeded for $command")
                        VibrationHelper.vibrate(this, VibrationHelper.PATTERN_SUCCESS)
                        broadcastResult(command, success = true)
                    }
                }, 800)
            }
        }
    }

    // ──────────────────────────────────────────────
    // Button search – depth-first traversal
    // ──────────────────────────────────────────────
    private fun findButtonNode(
        root: AccessibilityNodeInfo,
        labels: List<String>
    ): AccessibilityNodeInfo? {

        // Strategy 1: search by text content
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (n in nodes) {
                if (isClickable(n)) return n
                // Walk up to find a clickable ancestor
                val ancestor = findClickableAncestor(n)
                if (ancestor != null) return ancestor
            }
        }

        // Strategy 2: full tree walk – match text / contentDescription / viewIdResourceName
        return walkTree(root) { node ->
            if (!isClickable(node)) return@walkTree false
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val resId = node.viewIdResourceName?.lowercase() ?: ""

            labels.any { label ->
                val lc = label.lowercase()
                text.contains(lc) || desc.contains(lc) || resId.contains(lc)
            }
        }
    }

    private fun walkTree(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = walkTree(child, predicate)
            if (result != null) return result
        }
        return null
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node?.parent
        var depth = 0
        while (current != null && depth < 5) {
            if (isClickable(current)) return current
            current = current.parent
            depth++
        }
        return null
    }

    private fun isClickable(node: AccessibilityNodeInfo): Boolean =
        node.isClickable && node.isEnabled && node.isVisibleToUser

    // ──────────────────────────────────────────────
    // Click execution
    // ──────────────────────────────────────────────
    private fun performClickOnNode(node: AccessibilityNodeInfo) {
        // Try accessibility ACTION_CLICK first (most reliable)
        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        if (!clicked) {
            // Fallback: dispatch a tap gesture at the node's center
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                performTapGesture(
                    bounds.centerX().toFloat(),
                    bounds.centerY().toFloat()
                )
            }
        }
    }

    private fun performTapGesture(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "Gesture dispatch requires API 24+")
            return
        }
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 100L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Gesture tap completed at ($x, $y)")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Gesture tap cancelled")
            }
        }, null)
    }

    // ──────────────────────────────────────────────
    // Broadcast result back to overlay
    // ──────────────────────────────────────────────
    private fun broadcastResult(command: VoiceCommand, success: Boolean) {
        sendBroadcast(Intent("com.bepartner.voiceassist.COMMAND_RESULT").apply {
            putExtra("command", command.name)
            putExtra("success", success)
        })
    }
}
