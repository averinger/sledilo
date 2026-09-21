# Сохраняем наш код
-keep class com.violetbit.app.** { *; }

# WorkManager
-keep class androidx.work.** { *; }

# Gson (на всякий)
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
