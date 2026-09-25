package com.audiophile.musicplayer.playback.spatial;
import java.nio.ByteBuffer;
public final class NativeSpatial {
 private static final boolean AVAILABLE;
 static { boolean ok; try { System.loadLibrary("vantaSpatial"); ok=true; } catch (LinkageError e) {android.util.Log.e("VANTA_SPATIAL", "Native audio library unavailable", e);ok=false;} AVAILABLE=ok; }
 public static boolean isAvailable(){return AVAILABLE;}
 public static native long create(int mode, byte[] config);
 public static native void destroy(long handle);
 public static native int decode(long handle, ByteBuffer input, int size, long pts, ByteBuffer output);
 public static native int sampleRate(long handle);
 public static native long outputPts(long handle);
 public static native long createHeadphones(int sampleRate);
 public static native void destroyHeadphones(long handle);
 public static native void resetHeadphones(long handle);
 public static native int renderHeadphones(long handle, ByteBuffer input, int bytes, ByteBuffer output);
}
