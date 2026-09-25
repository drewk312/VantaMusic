package com.audiophile.musicplayer.playback.spatial;
import androidx.media3.common.Format;
import androidx.media3.decoder.*;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;
@UnstableApi
public final class SpatialDecoder extends SimpleDecoder<DecoderInputBuffer,SimpleDecoderOutputBuffer,DecoderException> {
 private final int mode; private final byte[] config; private long handle;
 private final ByteBuffer scratch=ByteBuffer.allocateDirect(2*1024*1024);
 public SpatialDecoder(Format format,int mode) throws DecoderException {
  super(new DecoderInputBuffer[8],new SimpleDecoderOutputBuffer[8]);this.mode=mode;
  config=format.initializationData.isEmpty()?new byte[0]:format.initializationData.get(0);
  try{handle=NativeSpatial.create(mode,config);}catch(RuntimeException e){throw new DecoderException("Spatial decoder initialization failed",e);}
  setInitialInputBufferSize(256*1024);
 }
 public String getName(){return mode==0?"OpenJOC headphones":"MPEG-H LC headphones";}
 protected DecoderInputBuffer createInputBuffer(){return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);}
 protected SimpleDecoderOutputBuffer createOutputBuffer(){return new SimpleDecoderOutputBuffer(this::releaseOutputBuffer);}
 protected DecoderException createUnexpectedDecodeException(Throwable e){return new DecoderException("Spatial decode failed",e);}
 @Nullable protected DecoderException decode(DecoderInputBuffer input,SimpleDecoderOutputBuffer output,boolean reset){
  try{
   if(reset){NativeSpatial.destroy(handle);handle=0;handle=NativeSpatial.create(mode,config);}
   int n=NativeSpatial.decode(handle,input.data,input.data.limit(),input.timeUs,scratch);
   if(n==0){output.shouldBeSkipped=true;return null;}
   if(n<0||n>scratch.capacity())return new DecoderException("Invalid spatial output size");
   scratch.position(0);scratch.limit(n);output.init(NativeSpatial.outputPts(handle),n).put(scratch).flip();scratch.clear();return null;
  }catch(RuntimeException e){return new DecoderException("Unsupported spatial recording",e);}
 }
 public int getSampleRate(){return NativeSpatial.sampleRate(handle);}
 @Override public void release(){super.release();if(handle!=0){NativeSpatial.destroy(handle);handle=0;}}
}
