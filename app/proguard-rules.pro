# R8 rules for release builds. Libraries (OkHttp, kotlinx.serialization, Coil, Play Billing,
# WorkManager, Compose) ship their own rules; these cover only this app's reflection.

# WorkManager instantiates workers by class name.
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# Keep readable stack traces in shared logs (line numbers only; names are still shortened).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
