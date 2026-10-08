# R8 rules for the CarPlay-only DiPlay build.
# Keep the entry points the platform instantiates by name.

# Activities, services and receivers are referenced from the manifest.
-keep class com.shilapi.xcertplay.** extends android.app.Activity { *; }
-keep class com.shilapi.xcertplay.** extends android.app.Service { *; }
-keep class com.shilapi.xcertplay.** extends android.content.BroadcastReceiver { *; }
-keep class com.shilapi.xcertplay.** extends android.app.Application { *; }
-keep class com.shilapi.xcertplay.** extends android.content.ContentProvider { *; }

# JmDNS discovers records reflectively.
-keep class javax.jmdns.** { *; }
-dontwarn javax.jmdns.**

# The MediaCodec/JNI surface used by the native I2C and radio helpers.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Kotlin metadata is needed for reflection-free coroutines and default args.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, SourceFile, LineNumberTable

# Keep Parcelable CREATOR fields.
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Enum valueOf/values used by persisted names.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Compose keeps its own runtime entry points.
-dontwarn androidx.compose.**
