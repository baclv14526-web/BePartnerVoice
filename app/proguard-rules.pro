# BePartner Voice – ProGuard rules

# Giữ lại tất cả class của app (service, receiver, activity)
-keep class com.bepartner.voiceassist.** { *; }

# Accessibility Service phải được giữ nguyên
-keep class * extends android.accessibilityservice.AccessibilityService { *; }

# SpeechRecognizer listener
-keep class * implements android.speech.RecognitionListener { *; }

# BroadcastReceiver
-keep class * extends android.content.BroadcastReceiver { *; }

# Không cảnh báo về thư viện Android internal
-dontwarn android.**
-dontwarn androidx.**
