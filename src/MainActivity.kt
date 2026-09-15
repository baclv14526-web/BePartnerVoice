package com.bepartner.voiceassist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bepartner.voiceassist.accessibility.BePartnerAccessibilityService
import com.bepartner.voiceassist.service.OverlayService
import com.bepartner.voiceassist.service.VoiceListenerService
import com.bepartner.voiceassist.ui.DebugActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_AUDIO = 100
    }

    private lateinit var tvA11yStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvMicStatus: TextView
    private lateinit var btnStartService: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvA11yStatus    = findViewById(R.id.tv_a11y_status)
        tvOverlayStatus = findViewById(R.id.tv_overlay_status)
        tvMicStatus     = findViewById(R.id.tv_mic_status)
        btnStartService = findViewById(R.id.btn_start_service)

        findViewById<Button>(R.id.btn_enable_a11y).setOnClickListener { openAccessibilitySettings() }
        findViewById<Button>(R.id.btn_enable_overlay).setOnClickListener { openOverlaySettings() }
        btnStartService.setOnClickListener { startAllServices() }
        findViewById<Button>(R.id.btn_view_commands).setOnClickListener { showCommandReference() }
        findViewById<Button>(R.id.btn_debug).setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }
        findViewById<Button>(R.id.btn_battery).setOnClickListener { openBatterySettings() }
    }

    override fun onResume() {
        super.onResume()
        updateStatusUI()
        requestMicPermission()
    }

    // ── Permission checks ────────────────────────────────────────
    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val myService = "${packageName}/${BePartnerAccessibilityService::class.java.name}"
        return enabled.contains(myService, ignoreCase = true)
    }

    private fun isOverlayGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    // ── UI ───────────────────────────────────────────────────────
    private fun updateStatusUI() {
        val a11y    = isAccessibilityEnabled()
        val overlay = isOverlayGranted()
        val mic     = hasMic()

        fun color(ok: Boolean) = ContextCompat.getColor(this, if (ok) R.color.green else R.color.red)

        tvA11yStatus.text    = if (a11y)    "✅ Accessibility: BẬT" else "❌ Accessibility: TẮT"
        tvA11yStatus.setTextColor(color(a11y))

        tvOverlayStatus.text = if (overlay) "✅ Overlay: BẬT"       else "❌ Overlay: TẮT"
        tvOverlayStatus.setTextColor(color(overlay))

        tvMicStatus.text     = if (mic)     "✅ Microphone: BẬT"    else "❌ Microphone: TẮT"
        tvMicStatus.setTextColor(color(mic))

        btnStartService.isEnabled = a11y && overlay && mic
        btnStartService.text = if (a11y && overlay && mic)
            "🚀 Bắt đầu lắng nghe" else "Hoàn tất các bước trên"
    }

    // ── Settings navigation ──────────────────────────────────────
    private fun openAccessibilitySettings() {
        AlertDialog.Builder(this)
            .setTitle("Bật Accessibility Service")
            .setMessage(
                "1. Chọn 'Ứng dụng đã cài đặt'\n" +
                "2. Tìm 'BePartner Voice Control'\n" +
                "3. Bật công tắc ON\n" +
                "4. Nhấn ALLOW"
            )
            .setPositiveButton("Mở Cài đặt") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        }
    }

    private fun requestMicPermission() {
        if (!hasMic()) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO) updateStatusUI()
    }

    // ── Start services ───────────────────────────────────────────
    private fun startAllServices() {
        val voiceIntent = Intent(this, VoiceListenerService::class.java).apply {
            action = VoiceListenerService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(voiceIntent)
        } else {
            startService(voiceIntent)
        }
        startService(Intent(this, OverlayService::class.java))

        Toast.makeText(this, "✅ Dịch vụ giọng nói đã khởi động!", Toast.LENGTH_LONG).show()
        window.decorView.postDelayed({ moveTaskToBack(true) }, 1500)
    }

    // ── Command reference ────────────────────────────────────────
    private fun showCommandReference() {
        AlertDialog.Builder(this)
            .setTitle("📖 Danh sách lệnh giọng nói")
            .setMessage(
                "── Chuyến xe ──\n" +
                "📍 \"Đã đến điểm đón\"\n" +
                "🚀 \"Bắt đầu chuyến đi\"\n" +
                "✅ \"Trả khách\" / \"Hoàn thành\"\n" +
                "👍 \"Chấp nhận chuyến\"\n" +
                "❌ \"Từ chối\"\n\n" +
                "── Giao hàng ──\n" +
                "📦 \"Đã đến điểm nhận hàng\"\n" +
                "🛍️ \"Đã nhận hàng\"\n" +
                "📷 \"Chụp ảnh nhận hàng\"\n" +
                "↩️ \"Trả hàng\"\n\n" +
                "── Online/Offline ──\n" +
                "🟢 \"Online\" / \"Vào ca\"\n" +
                "🔴 \"Offline\" / \"Nghỉ thôi\"\n" +
                "🛑 \"Ngừng nhận chuyến\"\n" +
                "🔁 \"Bật tắt\" → bật/tắt nút nhận cuốc BeBike\n" +
                "🎤 \"Bật micro\" → bật lắng nghe\n" +
                "🔇 \"Tắt micro\" → tắt lắng nghe\n\n" +
                "── Tài khoản ──\n" +
                "💰 \"Xem số dư\" / \"Kiểm tra số dư\"\n\n" +
                "── Điều hướng tab ──\n" +
                "🏠 \"Trang chủ\"\n" +
                "💵 \"Thu nhập\"\n" +
                "🛵 \"Dịch vụ\"\n" +
                "📬 \"Hộp thư\"\n" +
                "👤 \"Tôi\" / \"Trang cá nhân\"\n" +
                "📋 \"Lịch sử\"\n" +
                "📊 \"Tỉ lệ hoạt động\"\n\n" +
                "💡 Nói rõ, app tự lắng nghe liên tục."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // ── Tắt battery optimization – nguyên nhân phổ biến khiến ─────
    // ── Accessibility Service bị Android/Realme UI tự kill ngầm ───
    private fun openBatterySettings() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(packageName)
        if (isIgnoring) {
            Toast.makeText(this, "✅ Đã tắt tối ưu hóa pin cho app này rồi", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("🔋 Tắt tối ưu hóa pin")
            .setMessage(
                "Realme/ColorOS thường tự tắt app chạy nền để tiết kiệm pin, " +
                "khiến Accessibility Service bị ngắt ngẫu nhiên.\n\n" +
                "Nhấn 'Mở Cài đặt' rồi chọn 'Không tối ưu hóa' hoặc 'Cho phép' cho BePartner Voice."
            )
            .setPositiveButton("Mở Cài đặt") { _, _ ->
                try {
                    startActivity(Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    ))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
