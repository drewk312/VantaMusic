#include <jni.h>
#include <cmath>
#include <cstring>
#include <cstdlib>
#include <vector>
#include <algorithm>
#include <memory>
#include "openjoc.h"
#include "phonon.h"
#include "phonon_version.h"
#include "impeghd_api.h"
#include "impeghd_memory_standards.h"
#define JNI(name) Java_com_audiophile_musicplayer_playback_spatial_NativeSpatial_##name
static void fail(JNIEnv* e, const char* message) { e->ThrowNew(e->FindClass("java/lang/IllegalStateException"), message); }
struct Headphones {
 IPLContext context=nullptr; IPLHRTF hrtf=nullptr; IPLBinauralEffect effects[12]={}; int rate; std::vector<int16_t> carry;
 float hfL = 0.0f, hfR = 0.0f;
 explicit Headphones(int sr):rate(sr) {
  try {
  if (sr < 8000 || sr > 192000) throw "Invalid headphone sample rate";
  IPLContextSettings cs={}; cs.version=STEAMAUDIO_VERSION;
  if(iplContextCreate(&cs,&context)!=IPL_STATUS_SUCCESS) throw "Headphone context failed";
  IPLAudioSettings as={sr,128}; IPLHRTFSettings hs={}; hs.type=IPL_HRTFTYPE_DEFAULT; hs.volume=1;
  if(iplHRTFCreate(context,&as,&hs,&hrtf)!=IPL_STATUS_SUCCESS) throw "Headphone HRTF failed";
  IPLBinauralEffectSettings es={hrtf};
  for(int c=0;c<12;c++) if(iplBinauralEffectCreate(context,&as,&es,&effects[c])!=IPL_STATUS_SUCCESS) throw "Headphone effect failed";
  } catch (...) { cleanup(); throw; }
 }
 ~Headphones(){cleanup();}
 void cleanup(){for(auto &e:effects) if(e) iplBinauralEffectRelease(&e); if(hrtf)iplHRTFRelease(&hrtf);if(context)iplContextRelease(&context);}
 void reset(){carry.clear();hfL=0.0f;hfR=0.0f;for(auto e:effects) iplBinauralEffectReset(e);}
 static inline float softClip(float x) {
  if (x > 0.75f) {
   return 0.75f + 0.25f * std::tanh((x - 0.75f) / 0.25f);
  } else if (x < -0.75f) {
   return -0.75f - 0.25f * std::tanh((-x - 0.75f) / 0.25f);
  }
  return x;
 }
 // CICP 19: FL FR C LFE BL BR SL SR TFL TFR TBL TBR.
 int render(const int16_t* input,int frames,int16_t* output) {
  carry.insert(carry.end(),input,input+frames*12);
  frames=(int)(carry.size()/12/128)*128; input=carry.data();
  const float az[]={-30,30,0,0,-135,135,-90,90,-30,30,-135,135};
  const float el[]={0,0,0,0,0,0,0,0,30,30,30,30};
  float mono[128],left[128],right[128],mixL[128],mixR[128];float* in[]={mono};float* out[]={left,right};
  IPLAudioBuffer ib={1,128,in},ob={2,128,out};
  for(int base=0;base<frames;base+=128){
   std::fill_n(mixL,128,0);std::fill_n(mixR,128,0);
   for(int c=0;c<12;c++) {
    for(int n=0;n<128;n++) mono[n]=input[(base+n)*12+c]/32768.0f;
    if(c==3){
     for(int n=0;n<128;n++){
      mixL[n]+=mono[n]*1.20f;
      mixR[n]+=mono[n]*1.20f;
     }
     continue;
    }
    float a=az[c]*0.01745329252f,b=el[c]*0.01745329252f;
    IPLBinauralEffectParams p={};p.direction={std::sin(a)*std::cos(b),std::sin(b),-std::cos(a)*std::cos(b)};
    p.interpolation=IPL_HRTFINTERPOLATION_BILINEAR;p.spatialBlend=1;p.hrtf=hrtf;
    iplBinauralEffectApply(effects[c],&p,&ib,&ob);
    for(int n=0;n<128;n++){mixL[n]+=left[n];mixR[n]+=right[n];}
   }
   for(int n=0;n<128;n++){
    float diffL = mixL[n] - hfL;
    hfL += 0.30f * diffL;
    mixL[n] += 0.22f * diffL;

    float diffR = mixR[n] - hfR;
    hfR += 0.30f * diffR;
    mixR[n] += 0.22f * diffR;

    float outL = softClip(mixL[n] * 0.80f);
    float outR = softClip(mixR[n] * 0.80f);
    output[(base+n)*2]   = (int16_t)std::clamp(outL * 32767.0f, -32768.0f, 32767.0f);
    output[(base+n)*2+1] = (int16_t)std::clamp(outR * 32767.0f, -32768.0f, 32767.0f);
   }
  }
  carry.erase(carry.begin(),carry.begin()+frames*12);
  return frames*4;
 }
};
struct Decoder {
 int mode; openjoc_stream_decoder* joc=nullptr; ia_input_config in={};ia_output_config out={};std::vector<uint8_t> config,pending;std::unique_ptr<Headphones> hp;int sampleRate=48000;int64_t pts=0;
 explicit Decoder(int m):mode(m){}
 ~Decoder(){if(joc)openjoc_stream_decoder_destroy(joc);if(out.pv_ia_process_api_obj)ia_mpegh_dec_delete(&out);}
};
static void* aligned(UWORD32 n,UWORD32 a){void* p=nullptr;return posix_memalign(&p,std::max<size_t>(a,sizeof(void*)),(size_t)n+a)?nullptr:p;}
extern "C" JNIEXPORT jlong JNICALL JNI(create)(JNIEnv* e,jclass,jint mode,jbyteArray config){
 try{
  if (mode != 0 && mode != 1) throw "Unknown spatial decoder";
  auto d=std::make_unique<Decoder>(mode);
  if(mode==0){openjoc_decoder_config c={};openjoc_decoder_config_init_v1_4(&c);c.render_mode=OPENJOC_RENDER_SPEAKER;c.speaker_layout="7.1.4";
   if(openjoc_stream_decoder_create(&c,&d->joc)!=OPENJOC_STATUS_OK)throw "OpenJOC initialization failed";
  }else{
   d->in.ui_pcm_wd_sz=16;d->in.ui_cicp_layout_idx=19;d->in.i_preset_id=-1;d->in.ui_mhas_flag=1;
   int len=config?e->GetArrayLength(config):0;d->in.ui_raw_flag=len?1:0;
   d->out.malloc_mpegh=aligned;d->out.free_mpegh=free;
   if(ia_mpegh_dec_create(&d->in,&d->out)!=0)throw "Ittiam initialization failed";
   if(len){d->config.resize(len);e->GetByteArrayRegion(config,0,len,(jbyte*)d->config.data());d->pending=d->config;}
  }return (jlong)d.release();
 }catch(const char* m){fail(e,m);}catch(...){fail(e,"Spatial allocation failed");}return 0;
}
extern "C" JNIEXPORT void JNICALL JNI(destroy)(JNIEnv*,jclass,jlong h){delete (Decoder*)h;}
extern "C" JNIEXPORT jint JNICALL JNI(decode)(JNIEnv* e,jclass,jlong h,jobject input,jint size,jlong pts,jobject output){
 try{
  auto*d=(Decoder*)h;auto*src=(uint8_t*)e->GetDirectBufferAddress(input);auto*dst=(int16_t*)e->GetDirectBufferAddress(output);jlong cap=e->GetDirectBufferCapacity(output);
  if(!d||!src||!dst||size<0||size>e->GetDirectBufferCapacity(input))throw "Invalid spatial audio buffer";
  d->pts=pts;
  if(d->mode==0){
   auto st=openjoc_stream_decoder_send_chunk(d->joc,src,size,pts*48/1000,0);
   if(st!=OPENJOC_STATUS_OK&&st!=OPENJOC_STATUS_NEED_MORE_INPUT&&st!=OPENJOC_STATUS_FRAME_AVAILABLE)throw openjoc_stream_decoder_last_error(d->joc);
   int written=0;bool first=true;
   for(;;){openjoc_pcm_frame f={};openjoc_pcm_frame_init(&f);st=openjoc_stream_decoder_receive_frame(d->joc,&f);
    if(st==OPENJOC_STATUS_NEED_MORE_INPUT||st==OPENJOC_STATUS_END_OF_STREAM)break;
    if(st!=OPENJOC_STATUS_FRAME_AVAILABLE&&st!=OPENJOC_STATUS_OK)throw openjoc_stream_decoder_last_error(d->joc);
    if(!f.data||!f.data_len)break;
    if(f.sample_rate == 0 || f.channel_count!=12 || f.data_len%12 || f.data_len>12*32768 || written+(f.data_len/12+128)*4>(size_t)cap)throw "Unexpected OpenJOC output size";
    if(first){d->pts=f.pts_samples*1000000/f.sample_rate;first=false;}d->sampleRate=f.sample_rate;
    // OpenJOC's canonical 7.1.4 channel order matches CICP 19.
    // Steam Audio avoids the costly direct HRTF convolution on portable CPUs.
    std::vector<int16_t> speakers(f.data_len);
    for(size_t i=0;i<f.data_len;i++){if(!std::isfinite(f.data[i]))throw "Non-finite spatial output";speakers[i]=(int16_t)std::clamp(f.data[i]*32768.f,-32768.f,32767.f);}
    if(!d->hp || d->hp->rate != d->sampleRate)d->hp=std::make_unique<Headphones>(d->sampleRate);
    written+=d->hp->render(speakers.data(),f.data_len/12,dst+written/2);
   }
   return written;
  }
  if(d->pending.size()+size>2*1024*1024)throw "MPEG-H input limit exceeded";
  d->pending.insert(d->pending.end(),src,src+size);int written=0;
  for(int tries=0;tries<32&&!d->pending.empty();tries++){
   size_t n=std::min<size_t>(d->pending.size(),d->out.ui_inp_buf_size);memcpy(d->out.mem_info_table[IA_MEMTYPE_INPUT].mem_ptr,d->pending.data(),n);d->in.num_inp_bytes=n;d->out.num_out_bytes=0;d->out.i_bytes_consumed=0;
   int err=d->out.ui_init_done?ia_mpegh_dec_execute(d->out.pv_ia_process_api_obj,&d->in,&d->out):ia_mpegh_dec_init(d->out.pv_ia_process_api_obj,&d->in,&d->out);
   if(err<0)throw "Unsupported or damaged Low Complexity MPEG-H frame";
   int used=d->out.i_bytes_consumed;if(used<0||(size_t)used>n)throw "Invalid MPEG-H consumption";
   if(d->out.num_out_bytes>0){
    if(d->out.num_out_bytes > (int)d->out.mem_info_table[IA_MEMTYPE_OUTPUT].ui_size || d->out.num_out_bytes % 24) throw "Invalid MPEG-H PCM size";
    if(d->out.i_num_chan!=12)throw "Unexpected MPEG-H speaker layout";
    d->sampleRate=d->out.i_samp_freq;if(!d->hp || d->hp->rate != d->sampleRate)d->hp=std::make_unique<Headphones>(d->sampleRate);
    int frames=d->out.num_out_bytes/24;if(written+(frames+128)*4>cap)throw "MPEG-H output limit exceeded";
    written+=d->hp->render((int16_t*)d->out.mem_info_table[IA_MEMTYPE_OUTPUT].mem_ptr,frames,dst+written/2);
   }
   d->pending.erase(d->pending.begin(),d->pending.begin()+used);if(!used)break;
  }return written;
 }catch(const char*m){fail(e,m?m:"Spatial decoding failed");}catch(...){fail(e,"Spatial decoding failed");}return 0;
}
extern "C" JNIEXPORT jint JNICALL JNI(sampleRate)(JNIEnv*,jclass,jlong h){return ((Decoder*)h)->sampleRate;}
extern "C" JNIEXPORT jlong JNICALL JNI(outputPts)(JNIEnv*,jclass,jlong h){return ((Decoder*)h)->pts;}
extern "C" JNIEXPORT jlong JNICALL JNI(createHeadphones)(JNIEnv*e,jclass,jint rate){try{return (jlong)new Headphones(rate);}catch(...){fail(e,"Headphone renderer initialization failed");return 0;}}
extern "C" JNIEXPORT void JNICALL JNI(destroyHeadphones)(JNIEnv*,jclass,jlong h){delete (Headphones*)h;}
extern "C" JNIEXPORT void JNICALL JNI(resetHeadphones)(JNIEnv*,jclass,jlong h){if(h)((Headphones*)h)->reset();}
extern "C" JNIEXPORT jint JNICALL JNI(renderHeadphones)(JNIEnv*e,jclass,jlong h,jobject input,jint bytes,jobject output){try{
 if(!h||bytes<0||bytes%24||bytes>e->GetDirectBufferCapacity(input)||((bytes/24 + ((Headphones*)h)->carry.size()/12)/128)*128*4>e->GetDirectBufferCapacity(output))throw "Invalid headphone buffer";
 return ((Headphones*)h)->render((int16_t*)e->GetDirectBufferAddress(input),bytes/24,(int16_t*)e->GetDirectBufferAddress(output));
 }catch(const char*m){fail(e,m);return 0;}}
