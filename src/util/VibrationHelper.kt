package com.bepartner.voiceassist.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

object VibrationHelper {
    /** Short single pulse – button clicked successfully */
    val PATTERN_SUCCESS = longArrayOf(0, 80)
    /** Double pulse – button not found */
    val PATTERN_FAIL = longArrayOf(0, 80, 100, 80)

    fun vibrate(context: Context, pattern: LongArray) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}
