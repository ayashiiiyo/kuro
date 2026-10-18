# NewPipeExtractor
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-keep class com.grack.nanojson.** { *; }
-dontwarn com.grack.nanojson.**
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# Protobuf
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Kuro App
-keep class com.ayashii.kuro.** { *; }
