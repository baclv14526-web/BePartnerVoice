package com.bepartner.voiceassist.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bepartner.voiceassist.R
import com.bepartner.voiceassist.accessibility.BePartnerAccessibilityService
import java.text.SimpleDateFormat
import java.util.*

class DebugActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var tvA11yStatus: TextView
    private val logBuilder = SpannableStringBuilder()
    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.bepartner.voiceassist.STATUS_UPDATE" -> {
                    val status = intent.getStringExtra("status") ?: return
                    val detail = intent.getStringExtra("detail")
                    when (status) {
                        "ready"      -> log("🎤 Sẵn sàng nghe...", Color.GRAY)
                        "speaking"   -> log("🔊 Đang nhận giọng nói...", Color.CYAN)
                        "processing" -> log("⚙️  Xử lý...", Color.YELLOW)
                        "command_found" -> log("✅ Nhận lệnh: \"$detail\"", Color.GREEN)
                        "no_match"   -> log("❓ Không nhận ra: \"$detail\"", Color.YELLOW)
                        "partial"    -> log("... Nghe thấy: \"$detail\"", Color.GRAY)
                        "error"      -> log("⚠️  Lỗi STT: $detail", Color.RED)
                        "idle"       -> log("⏸  Micro tắt", Color.GRAY)
                        "listening"  -> log("🎤 Bắt đầu lắng nghe", Color.GREEN)
                    }
                }
                "com.bepartner.voiceassist.COMMAND_RESULT" -> {
                    val cmd     = intent.getStringExtra("command") ?: return
                    val success = intent.getBooleanExtra("success", false)
                    val btn     = intent.getStringExtra("button_found") ?: "(không rõ)"
                    if (success) {
                        log("✅ CLICK OK  |  lệnh=$cmd  |  nút=\"$btn\"", Color.GREEN)
                    } else {
                        log("❌ KHÔNG TÌM THẤY NÚT  |  lệnh=$cmd", Color.RED)
                        log("   → Nhấn [DUMP VIEWS] để xem tên nút thực tế", Color.RED)
                    }
                }
                "com.bepartner.voiceassist.SERVICE_STATE" -> {
                    val connected = intent.getBooleanExtra("connected", false)
                    if (connected) {
                        log("✅ Accessibility Service ĐÃ KẾT NỐI", Color.GREEN)
                    } else {
                        log("🔴 Accessibility Service BỊ NGẮT (hệ thống tắt hoặc app bị kill)", Color.RED)
                        log("   → Kiểm tra: Cài đặt > Pin > Tối ưu hóa pin > BePartner Voice > KHÔNG tối ưu", Color.RED)
                    }
                    onResume() // refresh status text
                }
                "com.bepartner.voiceassist.VIEW_DUMP" -> {
                    val dump = intent.getStringExtra("dump") ?: return
                    log("━━━ VIEW DUMP (BeBike) ━━━", Color.CYAN)
                    // In từng dòng để dễ đọc
                    dump.split("\n").forEach { line ->
                        if (line.contains("clickable=true") || line.contains("text=")) {
                            log(line.trim(), Color.WHITE)
                        }
                    }
                    log("━━━ KẾT THÚC DUMP ━━━", Color.CYAN)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)

        tvLog        = findViewById(R.id.tv_debug_log)
        scrollView   = findViewById(R.id.scroll_debug)
        tvA11yStatus = findViewById(R.id.tv_a11y_debug_status)

        // Dump toàn bộ views của BeBike đang mở
        findViewById<Button>(R.id.btn_dump_views).setOnClickListener {
            val a11y = BePartnerAccessibilityService.instance
            if (a11y != null) {
                log("📋 Đang dump views của BeBike...", Color.YELLOW)
                a11y.dumpCurrentWindow()
            } else {
                log("❌ Accessibility chưa bật!", Color.RED)
            }
        }

        // Test click thủ công từng lệnh (không cần nói)
        findViewById<Button>(R.id.btn_test_da_den).setOnClickListener    { sendTestCommand("DA_DEN") }
        findViewById<Button>(R.id.btn_test_bat_dau).setOnClickListener   { sendTestCommand("BAT_DAU") }
        findViewById<Button>(R.id.btn_test_tra_khach).setOnClickListener { sendTestCommand("TRA_KHACH") }
        findViewById<Button>(R.id.btn_test_chap_nhan).setOnClickListener { sendTestCommand("CHAP_NHAN") }
        findViewById<Button>(R.id.btn_test_so_du).setOnClickListener     { sendTestCommand("XEM_SO_DU") }

        // Xóa log
        findViewById<Button>(R.id.btn_clear_log).setOnClickListener {
            logBuilder.clear()
            tvLog.text = ""
            log("🗑️  Log đã xóa", Color.GRAY)
        }

        registerDebugReceiver()
        log("=== Debug Console BePartner Voice ===", Color.CYAN)
        log("1. Mở app BeBike Partner", Color.YELLOW)
        log("2. Quay lại đây, nhấn [DUMP VIEWS]", Color.YELLOW)
        log("3. Tìm dòng có clickable=true để thấy tên nút thực tế", Color.YELLOW)
        log("4. Hoặc dùng nút Test để click thử từng lệnh", Color.YELLOW)
    }

    override fun onResume() {
        super.onResume()
        val a11yOn = BePartnerAccessibilityService.instance != null
        tvA11yStatus.text  = if (a11yOn) "✅ Accessibility: ĐANG CHẠY" else "❌ Accessibility: CHƯA BẬT"
        tvA11yStatus.setTextColor(if (a11yOn) Color.GREEN else Color.RED)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    private fun sendTestCommand(name: String) {
        log("🧪 Test: $name", Color.YELLOW)
        sendBroadcast(Intent(BePartnerAccessibilityService.ACTION_EXECUTE_COMMAND).apply {
            putExtra(BePartnerAccessibilityService.EXTRA_COMMAND_KEY, name)
        })
    }

    private fun log(message: String, color: Int) {
        runOnUiThread {
            val time  = sdf.format(Date())
            val line  = "[$time] $message\n"
            val start = logBuilder.length
            logBuilder.append(line)
            logBuilder.setSpan(ForegroundColorSpan(color), start, logBuilder.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            tvLog.text = logBuilder
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun registerDebugReceiver() {
        val filter = IntentFilter().apply {
            addAction("com.bepartner.voiceassist.STATUS_UPDATE")
            addAction("com.bepartner.voiceassist.COMMAND_RESULT")
            addAction("com.bepartner.voiceassist.VIEW_DUMP")
            addAction("com.bepartner.voiceassist.SERVICE_STATE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }
}
