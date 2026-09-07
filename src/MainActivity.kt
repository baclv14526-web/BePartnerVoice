package com.bepartner.voiceassist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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

/**
 * MainActivity – one-time setup screen.
 *
 * After all permissions are granted and services enabled,
 * the driver can minimize this activity. The floating HUD
 * and voice service continue running in the background.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_AUDIO = 100
        private const val REQ_OVERLAY = 101
    }

    private lateinit var tvA11yStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvMicStatus: TextView
    private lateinit var btnEnableA11y: Button
    private lateinit var btnEnableOverlay: Button
    private lateinit var btnStartService: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvA11yStatus = findViewById(R.id.tv_a11y_status)
        tvOverlayStatus = findViewById(R.id.tv_overlay_status)
        tvMicStatus = findViewById(R.id.tv_mic_status)
        btnEnableA11y = findViewById(R.id.btn_enable_a11y)
        btnEnableOverlay = findViewById(R.id.btn_enable_overlay)
        btnStartService = findViewById(R.id.btn_start_service)

        btnEnableA11y.setOnClickListener { openAccessibilitySettings() }
        btnEnableOverlay.setOnClickListener { openOverlaySettings() }
        btnStartService.setOnClickListener { startAllServices() }

        // Show command reference card
        findViewById<Button>(R.id.btn_view_commands).setOnClickListener {
            showCommandReference()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusUI()
        requestMicPermission()
    }

    // ──────────────────────────────────────────────
    // Permission / setting checks
    // ──────────────────────────────────────────────
    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val myService = "${packageName}/${BePartnerAccessibilityService::class.java.name}"
        return enabledServices.contains(myService, ignoreCase = true)
    }

    private fun isOverlayPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun updateStatusUI() {
        val a11yOk = isAccessibilityEnabled()
        val overlayOk = isOverlayPermissionGranted()
        val micOk = hasMicPermission()

        tvA11yStatus.text = if (a11yOk) "✅ Accessibility: BẬT" else "❌ Accessibility: TẮT"
        tvA11yStatus.setTextColor(getColor(if (a11yOk) R.color.green else R.color.red))

        tvOverlayStatus.text = if (overlayOk) "✅ Overlay: BẬT" else "❌ Overlay: TẮT"
        tvOverlayStatus.setTextColor(getColor(if (overlayOk) R.color.green else R.color.red))

        tvMicStatus.text = if (micOk) "✅ Microphone: BẬT" else "❌ Microphone: TẮT"
        tvMicStatus.setTextColor(getColor(if (micOk) R.color.green else R.color.red))

        btnStartService.isEnabled = a11yOk && overlayOk && micOk
        btnStartService.text = if (a11yOk && overlayOk && micOk)
            "🚀 Bắt đầu lắng nghe" else "Hoàn tất các bước trên"
    }

    // ──────────────────────────────────────────────
    // Open settings
    // ──────────────────────────────────────────────
    private fun openAccessibilitySettings() {
        AlertDialog.Builder(this)
            .setTitle("Bật Accessibility Service")
            .setMessage(
                "1. Chọn 'Ứng dụng đã cài đặt' (hoặc 'Downloaded apps')\n" +
                "2. Tìm 'BePartner Voice Control'\n" +
                "3. Bật công tắc ON\n" +
                "4. Nhấn ALLOW khi được hỏi"
            )
            .setPositiveButton("Mở Cài đặt") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun requestMicPermission() {
        if (!hasMicPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQ_AUDIO
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO) updateStatusUI()
    }

    // ──────────────────────────────────────────────
    // Start services
    // ──────────────────────────────────────────────
    private fun startAllServices() {
        // Start voice listener
        val voiceIntent = Intent(this, VoiceListenerService::class.java).apply {
            action = VoiceListenerService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(voiceIntent)
        } else {
            startService(voiceIntent)
        }

        // Start overlay
        startService(Intent(this, OverlayService::class.java))

        Toast.makeText(this, "✅ Dịch vụ giọng nói đã khởi động!\nBạn có thể thu nhỏ ứng dụng.", Toast.LENGTH_LONG).show()

        // Give a moment then minimize
        window.decorView.postDelayed({ moveTaskToBack(true) }, 1500)
    }

    // ──────────────────────────────────────────────
    // Command reference dialog
    // ──────────────────────────────────────────────
    private fun showCommandReference() {
        val commands = buildString {
            appendLine("🎤 NÓI CÁC LỆNH SAU:\n")
            appendLine("📍 \"Đã đến điểm đón\" / \"Đã đến rồi\"")
            appendLine("   → Nhấn nút [Đã đến]\n")
            appendLine("🚀 \"Bắt đầu chuyến đi\" / \"Khởi hành\"")
            appendLine("   → Nhấn nút [Bắt đầu chuyến]\n")
            appendLine("✅ \"Trả khách\" / \"Hoàn thành\"")
            appendLine("   → Nhấn nút [Trả khách]\n")
            appendLine("👍 \"Chấp nhận chuyến\" / \"Nhận chuyến\"")
            appendLine("   → Nhấn nút [Chấp nhận]\n")
            appendLine("❌ \"Từ chối\" / \"Bỏ qua\"")
            appendLine("   → Nhấn nút [Từ chối]\n")
            appendLine("🟢 \"Online\" / \"Sẵn sàng\"")
            appendLine("   → Bắt đầu nhận chuyến\n")
            appendLine("🔴 \"Offline\" / \"Nghỉ thôi\"")
            appendLine("   → Dừng nhận chuyến\n")
            appendLine("💡 Mẹo: Nói rõ ràng, không cần từ wake-word.\n" +
                    "App tự động lắng nghe liên tục.")
        }

        AlertDialog.Builder(this)
            .setTitle("📖 Danh sách lệnh giọng nói")
            .setMessage(commands)
            .setPositiveButton("OK", null)
            .show()
    }
}
