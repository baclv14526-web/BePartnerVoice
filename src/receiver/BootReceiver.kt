package com.bepartner.voiceassist.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bepartner.voiceassist.service.OverlayService
import com.bepartner.voiceassist.service.VoiceListenerService

/**
 * BootReceiver – auto-starts voice service when phone reboots.
 * The driver doesn't have to open the app every morning.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val voiceIntent = Intent(context, VoiceListenerService::class.java).apply {
                action = VoiceListenerService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(voiceIntent)
            } else {
                context.startService(voiceIntent)
            }
            context.startService(Intent(context, OverlayService::class.java))
        }
    }
}
