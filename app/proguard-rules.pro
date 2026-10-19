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

# Media3 & Android Support (Consumer rules are packaged with Media3 AARs)
-dontwarn androidx.media3.**
-dontwarn android.support.v4.media.**

# NewPipeExtractor & Dependencies
-dontwarn org.schabi.newpipe.extractor.**
-keep class org.schabi.newpipe.extractor.** { <init>(...); }
-dontwarn com.grack.nanojson.**
-dontwarn org.jsoup.**
-dontwarn org.mozilla.javascript.**
-keep class org.mozilla.javascript.** { *; }
-dontwarn com.google.protobuf.**

# OkHttp & Okio (Consumer rules are packaged with OkHttp)
-dontwarn okhttp3.**
-dontwarn okio.**

# Kuro App Models & Auth Bridge
-keep class com.ayashii.kuro.data.model.** { *; }
-keep class com.ayashii.kuro.data.auth.UserAccount { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
