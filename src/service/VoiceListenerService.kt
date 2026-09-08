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
import com.bepartner.voiceassist.util.VoiceCommandParser

class VoiceListenerService : Service() {

    companion object {
        private const val TAG = "VoiceListenerSvc"
        private const val CHANNEL_ID = "bepartner_voice"
        private const val NOTIF_ID   = 1001
        const val ACTION_START  = "com.bepartner.voiceassist.START_VOICE"
        const val ACTION_STOP   = "com.bepartner.voiceassist.STOP_VOICE"
    }

    private var recognizer: SpeechRecognizer? = null
    private var isListening   = false
    private var shouldContinue = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotif("⏸ Chờ khởi động…"))
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(listener)
            }
        } else {
            Log.e(TAG, "SpeechRecognizer not available")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> { shouldContinue = true;  startListening() }
            ACTION_STOP  -> { shouldContinue = false; stopListening()  }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        shouldContinue = false
        recognizer?.destroy()
        super.onDestroy()
    }

    private fun startListening() {
        if (isListening || recognizer == null) return
        isListening = true
        recognizer!!.startListening(buildRecIntent())
        updateNotif("🎤 Đang nghe lệnh…")
        broadcast("listening", null)
    }

    private fun stopListening() {
        isListening = false
        recognizer?.stopListening()
        updateNotif("⏸ Tạm dừng")
        broadcast("idle", null)
    }

    private fun restart(delayMs: Long = 400) {
        if (!shouldContinue) return
        isListening = false
        android.os.Handler(mainLooper).postDelayed({ if (shouldContinue) startListening() }, delayMs)
    }

    private fun buildRecIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?)    { broadcast("ready", null) }
        override fun onBeginningOfSpeech()            { broadcast("speaking", null) }
        override fun onEndOfSpeech()                  { isListening = false; broadcast("processing", null) }
        override fun onBufferReceived(b: ByteArray?)  {}
        override fun onEvent(t: Int, p: Bundle?)      {}

        override fun onRmsChanged(rms: Float) {
            sendBroadcast(Intent("com.bepartner.voiceassist.RMS_UPDATE").putExtra("rms", rms))
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList<String>()
            val command = VoiceCommandParser.parse(matches)
            if (command != null) {
                broadcast("command_found", command.displayName)
                val a11y = BePartnerAccessibilityService.instance
                if (a11y != null) {
                    a11y.executeVoiceCommand(command)
                } else {
                    sendBroadcast(Intent(BePartnerAccessibilityService.ACTION_EXECUTE_COMMAND).apply {
                        putExtra(BePartnerAccessibilityService.EXTRA_COMMAND_KEY, command.name)
                    })
                }
            } else {
                broadcast("no_match", matches.firstOrNull())
            }
            restart(300)
        }

        override fun onPartialResults(partial: Bundle?) {
            val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text != null) broadcast("partial", text)
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT  -> restart(200)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restart(1000)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    shouldContinue = false
                    broadcast("error", "Cần quyền microphone")
                }
                else -> restart(500)
            }
        }
    }

    private fun broadcast(status: String, detail: String?) {
        sendBroadcast(Intent("com.bepartner.voiceassist.STATUS_UPDATE").apply {
            putExtra("status", status)
            if (detail != null) putExtra("detail", detail)
        })
    }

    // ── Notification ─────────────────────────────────────────────
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "BePartner Voice", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    private fun buildNotif(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BePartner Voice 🎤")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotif(text: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotif(text))
    }
}
