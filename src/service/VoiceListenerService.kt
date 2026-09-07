package com.bepartner.voiceassist.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bepartner.voiceassist.MainActivity
import com.bepartner.voiceassist.R
import com.bepartner.voiceassist.accessibility.BePartnerAccessibilityService
import com.bepartner.voiceassist.model.VoiceCommand
import com.bepartner.voiceassist.util.VoiceCommandParser
import java.util.Locale

/**
 * VoiceListenerService
 *
 * Runs as a foreground service (required for microphone access in background on Android 9+).
 * Uses Android's built-in SpeechRecognizer (Google engine) in continuous-listen mode.
 *
 * Flow:
 *   startListening() → SpeechRecognizer → onResults() → VoiceCommandParser.parse()
 *       → BePartnerAccessibilityService.executeVoiceCommand()
 *       → broadcast result → OverlayService updates UI
 *
 * Continuous mode: after each result (or error), listening restarts automatically.
 */
class VoiceListenerService : Service() {

    companion object {
        private const val TAG = "VoiceListenerSvc"
        private const val NOTIF_CHANNEL_ID = "bepartner_voice_channel"
        private const val NOTIF_ID = 1001

        const val ACTION_START = "com.bepartner.voiceassist.START_VOICE"
        const val ACTION_STOP = "com.bepartner.voiceassist.STOP_VOICE"
        const val ACTION_TOGGLE = "com.bepartner.voiceassist.TOGGLE_VOICE"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldContinue = true

    // ──────────────────────────────────────────────
    // Service lifecycle
    // ──────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Đang chờ lệnh giọng nói…"))
        initializeSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startListening()
            ACTION_STOP -> {
                shouldContinue = false
                stopListening()
            }
            ACTION_TOGGLE -> {
                if (isListening) {
                    shouldContinue = false
                    stopListening()
                } else {
                    shouldContinue = true
                    startListening()
                }
            }
        }
        return START_STICKY // restart if killed by system
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        shouldContinue = false
        speechRecognizer?.destroy()
        super.onDestroy()
    }

    // ──────────────────────────────────────────────
    // SpeechRecognizer setup
    // ──────────────────────────────────────────────
    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition not available on this device!")
            broadcastStatus("error", "Speech recognition không khả dụng")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(recognitionListener)
        }
        Log.i(TAG, "SpeechRecognizer initialized ✓")
    }

    private fun buildRecognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        // Vietnamese primary, English fallback
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_MATCH, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        // Shorter silence timeout = more responsive
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
    }

    // ──────────────────────────────────────────────
    // Listen control
    // ──────────────────────────────────────────────
    private fun startListening() {
        if (isListening) return
        isListening = true
        shouldContinue = true
        speechRecognizer?.startListening(buildRecognizerIntent())
        updateNotification("🎤 Đang nghe lệnh…")
        broadcastStatus("listening", null)
        Log.d(TAG, "Started listening")
    }

    private fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        updateNotification("⏸ Tạm dừng – nhấn để bắt đầu")
        broadcastStatus("idle", null)
        Log.d(TAG, "Stopped listening")
    }

    private fun restartListeningAfterDelay(delayMs: Long = 500) {
        if (!shouldContinue) return
        isListening = false
        android.os.Handler(mainLooper).postDelayed({
            if (shouldContinue) startListening()
        }, delayMs)
    }

    // ──────────────────────────────────────────────
    // RecognitionListener
    // ──────────────────────────────────────────────
    private val recognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
            broadcastStatus("ready", null)
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech began")
            broadcastStatus("speaking", null)
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Broadcast audio level for visual feedback in overlay
            sendBroadcast(Intent("com.bepartner.voiceassist.RMS_UPDATE").apply {
                putExtra("rms", rmsdB)
            })
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech, processing…")
            isListening = false
            broadcastStatus("processing", null)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?: emptyList<String>()

            Log.d(TAG, "Recognition results: $matches")

            val command = VoiceCommandParser.parse(matches)
            if (command != null) {
                Log.i(TAG, "Command matched: $command")
                executeCommand(command)
                broadcastStatus("command_found", command.displayName)
            } else {
                Log.d(TAG, "No command matched for: $matches")
                broadcastStatus("no_match", matches.firstOrNull())
            }

            // Always restart to keep listening
            restartListeningAfterDelay(300)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return
            broadcastStatus("partial", partial)
        }

        override fun onError(error: Int) {
            val errorMsg = speechErrorToString(error)
            Log.w(TAG, "Recognition error: $errorMsg ($error)")

            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Normal – just restart
                    restartListeningAfterDelay(200)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    restartListeningAfterDelay(1000)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    broadcastStatus("error", "Cần quyền microphone")
                    shouldContinue = false
                }
                else -> restartListeningAfterDelay(500)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ──────────────────────────────────────────────
    // Execute command via AccessibilityService
    // ──────────────────────────────────────────────
    private fun executeCommand(command: VoiceCommand) {
        val a11yService = BePartnerAccessibilityService.instance
        if (a11yService != null) {
            // Direct call if service is alive
            a11yService.executeVoiceCommand(command)
        } else {
            // Fallback: broadcast (AccessibilityService self-registers receiver)
            sendBroadcast(Intent(BePartnerAccessibilityService.ACTION_EXECUTE_COMMAND).apply {
                putExtra(BePartnerAccessibilityService.EXTRA_COMMAND_KEY, command.name)
            })
        }
    }

    // ──────────────────────────────────────────────
    // Broadcast helpers
    // ──────────────────────────────────────────────
    private fun broadcastStatus(status: String, detail: String?) {
        sendBroadcast(Intent("com.bepartner.voiceassist.STATUS_UPDATE").apply {
            putExtra("status", status)
            detail?.let { putExtra("detail", it) }
        })
    }

    // ──────────────────────────────────────────────
    // Notification
    // ──────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "BePartner Voice Control",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Dịch vụ điều khiển giọng nói cho BeBike"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("BePartner Voice 🎤")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notifManager = getSystemService(NotificationManager::class.java)
        notifManager?.notify(NOTIF_ID, buildNotification(text))
    }

    private fun speechErrorToString(error: Int) = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
        else -> "Unknown error"
    }
}
