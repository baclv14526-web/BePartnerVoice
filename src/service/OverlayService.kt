package com.bepartner.voiceassist.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import com.bepartner.voiceassist.R
import com.bepartner.voiceassist.model.VoiceCommand
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var tvStatus: TextView
    private lateinit var tvLastCommand: TextView
    private lateinit var btnMic: ImageButton
    private lateinit var viewMicLevel: View

    private val handler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var clearStatusRunnable: Runnable? = null

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.bepartner.voiceassist.STATUS_UPDATE" -> {
                    val status = intent.getStringExtra("status") ?: return
                    val detail = intent.getStringExtra("detail")
                    updateStatus(status, detail)
                }
                "com.bepartner.voiceassist.COMMAND_RESULT" -> {
                    val command = intent.getStringExtra("command") ?: return
                    val success = intent.getBooleanExtra("success", false)
                    showCommandResult(command, success)
                }
                "com.bepartner.voiceassist.RMS_UPDATE" -> {
                    val rms = intent.getFloatExtra("rms", 0f)
                    updateMicLevel(rms)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlay()
        registerStatusReceiver()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(statusReceiver) }
        runCatching { windowManager.removeView(overlayView) }
        super.onDestroy()
    }

    private fun createOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_hud, null)
        tvStatus      = overlayView.findViewById(R.id.tv_status)
        tvLastCommand = overlayView.findViewById(R.id.tv_last_command)
        btnMic        = overlayView.findViewById(R.id.btn_mic)
        viewMicLevel  = overlayView.findViewById(R.id.view_mic_level)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16; y = 120
        }

        windowManager.addView(overlayView, params)

        btnMic.setOnClickListener {
            val action = if (isListening) VoiceListenerService.ACTION_STOP
                         else VoiceListenerService.ACTION_START
            startService(Intent(this, VoiceListenerService::class.java).apply { this.action = action })
        }

        makeDraggable(overlayView, params)
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var startX = 0f; var startY = 0f
        var startPX = 0; var startPY = 0

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY
                    startPX = params.x;  startPY = params.y
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startX).toInt()
                    val dy = (event.rawY - startY).toInt()
                    if (abs(dx) > 5 || abs(dy) > 5) {
                        params.x = startPX - dx
                        params.y = startPY + dy
                        windowManager.updateViewLayout(view, params)
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    private fun updateStatus(status: String, detail: String?) {
        handler.post {
            when (status) {
                "listening", "ready" -> {
                    isListening = true
                    tvStatus.text = "🎤 Đang nghe…"
                    tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                    btnMic.setImageResource(R.drawable.ic_mic_active)
                }
                "speaking" -> {
                    tvStatus.text = "🔊 Đang nhận…"
                    tvStatus.setTextColor(Color.parseColor("#2196F3"))
                }
                "processing" -> {
                    tvStatus.text = "⚙️ Xử lý…"
                    tvStatus.setTextColor(Color.parseColor("#FF9800"))
                }
                "command_found" -> {
                    tvStatus.text = "✅ ${detail ?: "OK"}"
                    tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                    scheduleClearStatus()
                }
                "no_match" -> {
                    tvStatus.text = "❓ \"${detail?.take(20) ?: ""}\""
                    tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
                    scheduleClearStatus(2000)
                }
                "idle" -> {
                    isListening = false
                    tvStatus.text = "⏸ Tạm dừng"
                    tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
                    btnMic.setImageResource(R.drawable.ic_mic)
                }
                "error" -> {
                    isListening = false
                    tvStatus.text = "⚠️ ${detail ?: "Lỗi"}"
                    tvStatus.setTextColor(Color.parseColor("#F44336"))
                    scheduleClearStatus(3000)
                }
            }
        }
    }

    private fun showCommandResult(commandName: String, success: Boolean) {
        handler.post {
            val cmd = runCatching { VoiceCommand.valueOf(commandName) }.getOrNull()
            val label = cmd?.let { "${it.icon} ${it.displayName}" } ?: commandName
            tvLastCommand.text = if (success) "✅ $label" else "❌ $label"
            tvLastCommand.setTextColor(
                if (success) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
            )
            tvLastCommand.visibility = View.VISIBLE
            handler.postDelayed({ tvLastCommand.visibility = View.GONE }, 4000)
        }
    }

    private fun updateMicLevel(rms: Float) {
        handler.post {
            val normalized = ((rms + 2f) / 12f).coerceIn(0f, 1f)
            val parentWidth = (viewMicLevel.parent as? View)?.width ?: 200
            val lp = viewMicLevel.layoutParams
            lp.width = (parentWidth * normalized).toInt().coerceAtLeast(4)
            viewMicLevel.layoutParams = lp
        }
    }

    private fun scheduleClearStatus(delayMs: Long = 3000) {
        clearStatusRunnable?.let { handler.removeCallbacks(it) }
        clearStatusRunnable = Runnable {
            if (isListening) {
                tvStatus.text = "🎤 Đang nghe…"
                tvStatus.setTextColor(Color.parseColor("#4CAF50"))
            }
        }.also { handler.postDelayed(it, delayMs) }
    }

    private fun registerStatusReceiver() {
        val filter = IntentFilter().apply {
            addAction("com.bepartner.voiceassist.STATUS_UPDATE")
            addAction("com.bepartner.voiceassist.COMMAND_RESULT")
            addAction("com.bepartner.voiceassist.RMS_UPDATE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
    }
}
