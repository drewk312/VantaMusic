package com.audiophile.musicplayer.playback.spatial;
import android.os.Handler;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.*;
import androidx.media3.exoplayer.audio.*;
@UnstableApi
public final class SpatialAudioRenderer extends DecoderAudioRenderer<SpatialDecoder> {
 private final int mode;
 public SpatialAudioRenderer(int mode,Handler handler,AudioRendererEventListener listener,AudioSink sink){super(handler,listener,sink);this.mode=mode;}
 public String getName(){return mode==0?"OpenJocAudioRenderer":"IttiamMpeghAudioRenderer";}
 protected int supportsFormatInternal(Format f){
  if(!NativeSpatial.isAvailable()||f.cryptoType!=C.CRYPTO_TYPE_NONE)return C.FORMAT_UNSUPPORTED_TYPE;
  boolean match=mode==0?SpatialMimeSupport.isOpenJocInput(f):SpatialMimeSupport.isIttiamMpeghInput(f);
  return match?C.FORMAT_HANDLED:C.FORMAT_UNSUPPORTED_TYPE;
 }
 protected SpatialDecoder createDecoder(Format f,CryptoConfig crypto) throws DecoderException{return new SpatialDecoder(f,mode);}
 protected Format getOutputFormat(SpatialDecoder d){return new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setPcmEncoding(C.ENCODING_PCM_16BIT).setChannelCount(2).setSampleRate(d.getSampleRate()).build();}
}
