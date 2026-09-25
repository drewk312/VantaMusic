# ==============================================================================
# VANTA Hardened Security & Anti-Reverse Engineering ProGuard/R8 Rules
# ==============================================================================

# Flatten all internal class packages into a single obfuscated root
# Erases architectural layout from reverse-engineering tools (Jadx/APKTool/Bytecode viewer)
-repackageclasses 'com.audiophile.musicplayer.o'
-allowaccessmodification

# Strip all source file names, line number tables, and debugging metadata
-renamesourcefileattribute ""
-keepattributes Exceptions,InnerClasses,Signature,EnclosingMethod,*Annotation*

# Strip all Android logs and diagnostics in production
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static java.lang.String getStackTraceString(java.lang.Throwable);
}

# Preserve native JNI bindings (Required for C++ audio engines to function)
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.audiophile.musicplayer.playback.spatial.NativeSpatial { *; }
-keep class com.audiophile.musicplayer.playback.dsp.VantaEqualizerNative { *; }
-keep class androidx.media3.decoder.mpegh.** { *; }
-keep class androidx.media3.decoder.iamf.** { *; }

# Media3 Session & Android Auto Discovery Entrypoints
-keep class androidx.media3.session.** { *; }
-keep class com.audiophile.musicplayer.playback.PlaybackService { *; }
-keep class com.audiophile.musicplayer.playback.MediaSessionTrustPolicy { *; }
-keep class com.audiophile.musicplayer.playback.PackageValidator { *; }

# Preserve Gson Serialized Data Models & Generic TypeTokens
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keepclassmembers class * extends com.google.gson.reflect.TypeToken { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Lyrics data models (Gson serialization — must preserve field names and generic signatures)
-keep class com.audiophile.musicplayer.data.lyrics.** { *; }
-keepclassmembers class com.audiophile.musicplayer.data.lyrics.** { *; }

# Room Database entities, DAOs & TypeConverters
-keep class com.audiophile.musicplayer.data.local.Converters { *; }
-keep class com.audiophile.musicplayer.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# ==============================================================================
# Retrofit 2.9.0 Kotlin suspend fix — keep Continuation<T>'s type parameter
# R8 was repackaging kotlin.coroutines.Continuation -> o.go0 and REMOVING its
# generic type argument (resumeWith(LObject;)V). Retrofit's HttpServiceMethod
# casts the continuation param to ParameterizedType to recover the suspend
# function's return type, so the raw Continuation caused:
#   java.lang.ClassCastException: java.lang.Class cannot be cast
#   to java.lang.reflect.ParameterizedType   (every search, release builds)
# Keeping the class preserves `Signature` <T:Ljava/lang/Object;> and thus the
# `Continuation<ResponseType>` arguments Retrofit reflects on.
-keep class kotlin.coroutines.Continuation { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Deezer metadata DTOs must be kept as classes: Retrofit reads the suspend
# `Continuation<DeezerXxx>` generic off the interface method's Signature to
# pick the response type. R8 deleted these DTOs (they were only referenced
# through generic signatures) and rewrote the Signature arg to the erased
# bound `Object`, which would make Retrofit deserialize JSON into a
# LinkedTreeMap and then fail with a cast error. Keep them live.
-keep class com.audiophile.musicplayer.data.metadata.deezer.** { *; }
