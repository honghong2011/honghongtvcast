# ==========================================================
# Cling UPnP/DLNA 协议栈混淆规则
# Cling 大量使用反射和注解绑定，必须 keep 相关类
# ==========================================================
-keep class org.fourthline.cling.** { *; }
-keep class org.seamless.** { *; }
-dontwarn org.fourthline.cling.**
-dontwarn org.seamless.**

# ==========================================================
# ExoPlayer 混淆规则
# ==========================================================
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# ==========================================================
# OkHttp 混淆规则
# ==========================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ==========================================================
# Kotlin 反射支持
# ==========================================================
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlin.**

# 保留行号信息，方便调试崩溃栈
-keepattributes SourceFile,LineNumberTable
