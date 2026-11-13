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

# R8 DEX compaction and debug log stripping for smaller release APKs
-allowaccessmodification
-repackageclasses 'kuro.p'
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Media3 & Android Support (Consumer rules are packaged with Media3 AARs)
-dontwarn androidx.media3.**
-dontwarn android.support.v4.media.**

# NewPipeExtractor: keep core entrypoints & YouTube service while allowing R8 to tree-shake unused services
-dontwarn org.schabi.newpipe.extractor.**
-keep class org.schabi.newpipe.extractor.NewPipe { *; }
-keep class org.schabi.newpipe.extractor.ServiceList { *; }
-keep class org.schabi.newpipe.extractor.StreamingService { *; }
-keep class org.schabi.newpipe.extractor.services.youtube.** { *; }
-keep class * extends org.schabi.newpipe.extractor.StreamingService {
    public <init>(...);
}

# NanoJSON & Jsoup: allow R8 dead-code elimination on unused HTML/DOM methods
-dontwarn com.grack.nanojson.**
-keep class com.grack.nanojson.** { *; }
-dontwarn org.jsoup.**

# Mozilla Rhino: keep reflection-loaded runtime & VMBridge classes, strip unused debugger/tools
-dontwarn org.mozilla.javascript.**
-keep class org.mozilla.javascript.Context { *; }
-keep class org.mozilla.javascript.ContextFactory { *; }
-keep class org.mozilla.javascript.Scriptable { *; }
-keep class org.mozilla.javascript.ScriptableObject { *; }
-keep class org.mozilla.javascript.IdScriptableObject { *; }
-keep class org.mozilla.javascript.Function { *; }
-keep class org.mozilla.javascript.BaseFunction { *; }
-keep class org.mozilla.javascript.Native* { *; }
-keep class org.mozilla.javascript.VMBridge { *; }
-keep class org.mozilla.javascript.jdk15.** { *; }
-keep class org.mozilla.javascript.jdk18.** { *; }
-keep class * extends org.mozilla.javascript.VMBridge { *; }
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

# Kuro Native Core (Rust JNI)
-keepclasseswithmembernames class com.ayashii.kuro.data.native.KuroNativeCore {
    native <methods>;
}
-keep class com.ayashii.kuro.data.native.** { *; }
