# ─── BeatDrop ProGuard / R8 rules ──────────────────────────────────────────────
# Keep enough for Media3, Coil, Compose, Kotlin coroutines, and our model classes
# so release (minified) builds behave like debug.

# Kotlin metadata + coroutines
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { *; }

# AndroidX Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# OkHttp / Okio (transitive via Coil)
-dontwarn okhttp3.**
-dontwarn okio.**

# Jetpack Compose keeps most of what it needs via consumer rules; add safety nets
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# DataStore / protobuf-lite
-keep class androidx.datastore.*.** { *; }
-dontwarn androidx.datastore.**

# Our own data models (used with org.json + persistence) — keep names/fields
-keep class com.beatdrop.kt.data.** { *; }
-keep class com.beatdrop.kt.youtube.** { *; }

# org.json is part of the platform; no rules needed.

# Enums (used as persisted values)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable/Serializable contracts if any are added later
-keepnames class * implements android.os.Parcelable
-keepclassmembers class * implements java.io.Serializable { *; }

# Strip Android logging in release for cleanliness
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep @JavascriptInterface methods used by YoutubeExtractor and the IFrame bridge
-keepclassmembers class com.beatdrop.kt.youtube.YoutubeExtractor {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# Keep OkHttp internals
-dontwarn okhttp3.**
-dontwarn okio.**
