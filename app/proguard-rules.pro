# Preserve Parcelables and Bundleables for Binder IPC (MediaController <-> PlaybackService)
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers class **$CREATOR {
    *;
}
-keepclassmembers class * implements androidx.media3.common.Bundleable {
    public static final ** CREATOR;
}

# Keep attributes for reflection and serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Media3 / ExoPlayer / Session
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class android.support.v4.media.** { *; }
-dontwarn android.support.v4.media.**

# NewPipeExtractor & Dependencies
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-keep class com.grack.nanojson.** { *; }
-dontwarn com.grack.nanojson.**
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# OkHttp & Okio
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**

# Kuro App
-keep class com.ayashii.kuro.** { *; }
