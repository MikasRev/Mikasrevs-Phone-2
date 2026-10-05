package com.mikasrevs.newphone.client.voice;

import com.google.gson.Gson;
import com.mikasrevs.newphone.network.*;
import com.mikasrevs.newphone.network.message.PhoneVoicePacket;
import java.io.*;
import java.util.*;
import javax.sound.sampled.*;

/** Microphone access is explicit; capture/playback never block the render thread. */
public final class PhoneVoiceNotes {
    private static final java.util.concurrent.ThreadPoolExecutor PROCESSING=new java.util.concurrent.ThreadPoolExecutor(
            1,1,0,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<>(8),
            job->{Thread t=new Thread(job,"MPhone voice processing");t.setDaemon(true);return t;});
    private static final java.util.concurrent.Semaphore AUDIO_SLOTS=new java.util.concurrent.Semaphore(2);
    private static final java.util.concurrent.ThreadPoolExecutor DEVICE_CLOSE=new java.util.concurrent.ThreadPoolExecutor(
            2,2,0,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<>(16),
            job->{Thread t=new Thread(job,"MPhone audio device close");t.setDaemon(true);return t;});
    private static volatile long transportSession;
    private static long downloadSequence,lastDownloadAt;
    private static final Map<String,String> ERRORS=new LinkedHashMap<>();
    private static PhoneVoiceTransfer upload;
    private static String uploadId="";
    private static Object uploadConnection;
    private static long uploadStarted,playStarted;
    private static final AudioFormat FORMAT=new AudioFormat(PhoneVoiceCodec.SAMPLE_RATE,16,1,true,false);
    private static volatile boolean recording,preparing,playing;
    private static volatile byte[] recorded;
    private static volatile String error="",playingId="";
    private static volatile TargetDataLine microphone;private static volatile SourceDataLine speaker;
    private static volatile long generation,playGeneration,recordStarted;
    private static volatile long capturedMillis,playingDuration;
    private static volatile float[] recordingWave=new float[44];
    private static final float[] EMPTY_WAVE=new float[0];
    private record Visual(long millis,float[] wave) { }
    private static final Map<String,Visual> VISUALS=new HashMap<>();
    private static final Map<String,byte[]> CACHE=new LinkedHashMap<>(8,0.75f,true);
    private static PhoneVoiceCodec.Transfer download;private static String downloadId="";private static long requestedAt;
    private PhoneVoiceNotes() { }
    public static boolean recording(){return recording;}public static boolean preparing(){return preparing;}public static boolean playing(){return playing;}
    public static byte[] recorded(){return recorded;}public static String error(){return error;}
    public static long elapsed(){return Math.max(0,System.currentTimeMillis()-recordStarted);}
    public static long durationMillis(){return capturedMillis;}
    public static float[] recordingWaveform(){return recordingWave;}
    public static long durationMillis(String id){var visual=VISUALS.get(id);return visual==null?0:visual.millis;}
    public static float[] waveform(String id){var visual=VISUALS.get(id);return visual==null?EMPTY_WAVE:visual.wave;}
    public static long playbackMillis(){
        var line=speaker;if(!playing||line==null)return 0;
        try{return Math.min(playingDuration,line.getLongFramePosition()*1000L/PhoneVoiceCodec.SAMPLE_RATE);}
        catch(RuntimeException ignored){return 0;}
    }
    public static float playbackProgress(){return playingDuration<=0?0:Math.min(1,playbackMillis()/(float)playingDuration);}

    private static long pcmMillis(int length){return length*1000L/(PhoneVoiceCodec.SAMPLE_RATE*2);}
    private static float amplitude(byte[] pcm,int start,int end){
        double sum=0;int count=0;
        for(int i=start;i+1<end;i+=2){double sample=(short)((pcm[i]&255)|(pcm[i+1]<<8))/32768.0;sum+=sample*sample;count++;}
        return count==0?0:(float)Math.min(1,Math.sqrt(sum/count)*3);
    }
    private static float[] envelope(byte[] pcm){
        float[] wave=new float[44];int samples=pcm.length/2;
        for(int i=0;i<wave.length;i++)wave[i]=amplitude(pcm,(i*samples/wave.length)*2,((i+1)*samples/wave.length)*2);
        return wave;
    }
    private static void cache(String id,byte[] bytes,byte[] pcm){
        cache(id,bytes,new Visual(pcmMillis(pcm.length),envelope(pcm)));
    }
    private static void cache(String id,byte[] bytes,Visual visual){
        CACHE.put(id,bytes);VISUALS.put(id,visual);
        while(CACHE.size()>8){String oldest=CACHE.keySet().iterator().next();CACHE.remove(oldest);VISUALS.remove(oldest);}
    }
    public static synchronized void begin(){
        cancel();error="";recorded=null;recording=true;recordStarted=System.currentTimeMillis();long token=generation;
        audioWorker(()->{ByteArrayOutputStream pcm=new ByteArrayOutputStream();TargetDataLine line=null;
            try{line=AudioSystem.getTargetDataLine(FORMAT);line.open(FORMAT);
                synchronized(PhoneVoiceNotes.class){if(token!=generation||!recording)return;microphone=line;line.start();}
                byte[] buffer=new byte[3200];
                while(recording&&token==generation&&pcm.size()<PhoneVoiceCodec.MAX_PCM){
                    int n=line.read(buffer,0,Math.min(buffer.length,PhoneVoiceCodec.MAX_PCM-pcm.size()));
                    if(n<=0)break;pcm.write(buffer,0,n);
                    synchronized(PhoneVoiceNotes.class){if(token==generation){
                        float[] wave=new float[44];System.arraycopy(recordingWave,1,wave,0,43);
                        wave[43]=amplitude(buffer,0,n);recordingWave=wave;capturedMillis=pcmMillis(pcm.size());
                    }}
                }
            }catch(Exception e){synchronized(PhoneVoiceNotes.class){if(token==generation&&recording)error=com.mikasrevs.newphone.client.settings.PhoneSettingsStore.text("Mikrofon açılamadı veya kayıt kesildi.","Could not open microphone or recording was interrupted.");}}
            finally{closeLine(line);
                synchronized(PhoneVoiceNotes.class){if(token!=generation)return;microphone=null;recording=false;preparing=true;}
                byte[] encoded=null;String failure="";
                try{if(error.isBlank())encoded=PhoneVoiceCodec.encode(pcm.toByteArray());}
                catch(IOException e){failure=com.mikasrevs.newphone.client.settings.PhoneSettingsStore.text("Kayıt çok kısa. Biraz daha uzun basılı tut.","Recording too short. Hold a little longer.");}
                float[] wave=envelope(pcm.toByteArray());
                synchronized(PhoneVoiceNotes.class){if(token==generation){recorded=encoded;recordingWave=wave;capturedMillis=pcmMillis(pcm.size());if(!failure.isBlank())error=failure;preparing=false;}}
            }
        }, "", true);
    }
    public static synchronized void finish(){if(!recording)return;recording=false;preparing=true;var line=microphone;if(line!=null)worker(()->closeLine(line));}
    public static synchronized void cancel(){cancelDownload();generation++;recording=false;preparing=false;recorded=null;capturedMillis=0;recordingWave=new float[44];error="";var line=microphone;microphone=null;if(line!=null)worker(()->closeLine(line));stopPlayback();}
    public static synchronized void stopPlayback(){playGeneration++;playing=false;playingId="";var line=speaker;speaker=null;if(line!=null)worker(()->closeLine(line));}
    private static void closeLine(DataLine line){if(line!=null)try{line.stop();line.close();}catch(RuntimeException ignored){}}
    public static boolean playing(String id){return playing&&id.equals(playingId);}
    public static boolean loading(String id){return !downloadId.isEmpty()&&id.equals(downloadId);}
    public static String error(String id){return ERRORS.getOrDefault(id,"");}
    private static String text(String tr,String en){return com.mikasrevs.newphone.client.settings.PhoneSettingsStore.text(tr,en);}
    private static void error(String id,String message){
        ERRORS.put(id,message);while(ERRORS.size()>32)ERRORS.remove(ERRORS.keySet().iterator().next());
    }
    private static void cancelDownload(){
        boolean active=!downloadId.isEmpty();downloadSequence++;downloadId="";download=null;
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(active&&mc!=null&&mc.getConnection()!=null)PhoneNetwork.sendAction("VOICE_CANCEL","","");
    }
    public static void preview(){if(playing){stopPlayback();return;}if(recorded!=null)play(recorded,"");}
    public static void playMessage(String id){
        error="";ERRORS.remove(id);
        if(id.equals(playingId)){stopPlayback();return;}
        if(loading(id)){cancelDownload();return;}
        cancelDownload();stopPlayback();
        byte[] bytes=CACHE.get(id);
        if(bytes!=null){play(bytes,id);return;}
        downloadId=id;download=null;requestedAt=lastDownloadAt=System.currentTimeMillis();
        PhoneNetwork.sendAction("VOICE_GET",id,"");
    }
    private static void play(byte[] bytes,String id){play(bytes,id,null);}
    private static synchronized void play(byte[] bytes,String id,byte[] decoded){
        stopPlayback();playing=true;playingId=id;playingDuration=0;playStarted=System.currentTimeMillis();long token=playGeneration;
        audioWorker(()->{SourceDataLine line=null;try{byte[] pcm=decoded==null?PhoneVoiceCodec.decode(bytes):decoded;float volume=com.mikasrevs.newphone.client.settings.PhoneSettingsStore.phoneVolume();for(int i=0;i<pcm.length;i+=2){int sample=(short)((pcm[i]&255)|(pcm[i+1]<<8));int scaled=Math.round(sample*volume);pcm[i]=(byte)scaled;pcm[i+1]=(byte)(scaled>>8);}line=AudioSystem.getSourceDataLine(FORMAT);line.open(FORMAT);synchronized(PhoneVoiceNotes.class){if(token!=playGeneration||!playing)return;speaker=line;line.start();}
            synchronized(PhoneVoiceNotes.class){if(token==playGeneration)playingDuration=pcmMillis(pcm.length);}
            for(int at=0;at<pcm.length&&playing&&token==playGeneration;){int n=line.write(pcm,at,Math.min(3200,pcm.length-at));if(n<=0)break;at+=n;}if(playing&&token==playGeneration)line.drain();
        }catch(Exception e){synchronized(PhoneVoiceNotes.class){if(token==playGeneration){error=text("Ses çalınamadı. Ses aygıtını kontrol edip yeniden dokun.","Could not play audio. Check your audio device and tap again.");
            String reason=error;long session=transportSession;
            net.minecraft.client.Minecraft.getInstance().execute(()->{if(session==transportSession&&token==playGeneration&&!id.isBlank())error(id,reason);});}}}finally{closeLine(line);synchronized(PhoneVoiceNotes.class){if(token==playGeneration){speaker=null;playing=false;playingId="";}}}}, id, false);
    }
    public static void receive(PhoneVoicePacket p){
        if(!p.request().equals(downloadId)||downloadId.isEmpty())return;
        if("ERROR".equals(p.kind())){
            String reason=new String(p.bytes(),java.nio.charset.StandardCharsets.UTF_8);
            error(downloadId,reason);cancelDownload();return;
        }
        lastDownloadAt=System.currentTimeMillis();
        try{
            if(p.index()==0)download=new PhoneVoiceCodec.Transfer(p.count());
            if(download==null)return;
            if(download.add(p.index(),p.count(),p.bytes())){
                byte[] bytes=download.bytes();download=null;
                String id=downloadId;long sequence=downloadSequence,session=transportSession;
                PROCESSING.execute(()->{
                    try{
                        byte[] pcm=PhoneVoiceCodec.decode(bytes);Visual visual=new Visual(pcmMillis(pcm.length),envelope(pcm));
                        net.minecraft.client.Minecraft.getInstance().execute(()->{
                            if(session!=transportSession||sequence!=downloadSequence||!id.equals(downloadId))return;
                            cache(id,bytes,visual);downloadId="";ERRORS.remove(id);play(bytes,id,pcm);
                        });
                    }catch(Exception e){net.minecraft.client.Minecraft.getInstance().execute(()->{
                        if(session!=transportSession||sequence!=downloadSequence||!id.equals(downloadId))return;
                        error(id,text("Ses kaydı okunamadı. Yeniden dokun.","Could not decode the recording. Tap again."));cancelDownload();
                    });}
                });
            }
        }catch(RuntimeException e){error(downloadId,text("Ses indirilemedi. Yeniden dokun.","Download failed. Tap again."));cancelDownload();}
    }

    public static boolean uploading(){return upload!=null;}
    public static boolean uploading(String id){return upload!=null&&uploadId.equals(id);}
    public static void cancelUpload(String id){if(id.equals(uploadId)){upload=null;uploadId="";}}
    public static void upload(PhoneSendRequest request,byte[] media){
        if(upload!=null)throw new IllegalStateException("Voice upload already running");
        upload=new PhoneVoiceTransfer("UPLOAD",new Gson().toJson(request),media);
        uploadId=request.id;uploadStarted=System.currentTimeMillis();
        uploadConnection=net.minecraft.client.Minecraft.getInstance().getConnection();
        long session=transportSession;
        try{PROCESSING.execute(()->{
            try{byte[] pcm=PhoneVoiceCodec.decodeUpload(media);Visual visual=new Visual(pcmMillis(pcm.length),envelope(pcm));
                net.minecraft.client.Minecraft.getInstance().execute(()->{if(session==transportSession)cache(request.id,media,visual);});
            }catch(IOException ignored){/* The server returns a rejection for invalid audio. */}
        });}catch(java.util.concurrent.RejectedExecutionException ignored){/* Waveform can be loaded on first playback. */}
    }

    public static void tick(){
        var mc=net.minecraft.client.Minecraft.getInstance();long now=System.currentTimeMillis();
        if(upload!=null){
            String id=uploadId;
            try{
                if(mc==null||mc.getConnection()==null||mc.getConnection()!=uploadConnection||now-uploadStarted>60_000)throw new IllegalStateException();
                upload.send(2,p->PhoneNetwork.CHANNEL.sendToServer(p));
                if(upload.complete()){upload=null;uploadId="";}
            }catch(RuntimeException e){
                upload=null;uploadId="";
                com.mikasrevs.newphone.client.data.PhoneMessageClient.uploadFailed(id,text("Ses gönderimi kesildi. Kayıt korundu; yeniden dene.","Voice upload was interrupted. Your recording is saved; retry."));
            }
        }
        if(!downloadId.isEmpty()&&(now-lastDownloadAt>15_000||now-requestedAt>90_000)){
            error(downloadId,text("Ses indirme zaman aşımına uğradı. Yeniden dokun.","Voice download timed out. Tap again."));cancelDownload();
        }
        if(playing&&speaker==null&&now-playStarted>10_000){
            String id=playingId;stopPlayback();error=text("Ses aygıtı yanıt vermiyor. Yeniden dene.","Audio device is not responding. Try again.");
            if(!id.isBlank())error(id,error);
        }
        if(recording&&microphone==null&&now-recordStarted>10_000){
            cancel();error=text("Mikrofon yanıt vermiyor. Yeniden dene.","Microphone is not responding. Try again.");
        }
    }
    public static void reset(){transportSession++;cancelDownload();cancel();upload=null;uploadId="";CACHE.clear();VISUALS.clear();ERRORS.clear();PROCESSING.getQueue().clear();error="";}
    private static void audioWorker(Runnable job,String id,boolean capture){
        if(!AUDIO_SLOTS.tryAcquire()){
            error=text("Ses aygıtı meşgul. Biraz bekleyip yeniden dene.","Audio device is busy. Wait a moment and retry.");
            if(capture){recording=false;preparing=false;}else{playing=false;playingId="";if(!id.isBlank())error(id,error);}
            return;
        }
        Thread thread=new Thread(()->{try{job.run();}finally{AUDIO_SLOTS.release();}},"MPhone voice note");
        thread.setDaemon(true);thread.start();
    }
    private static void worker(Runnable job){
        try{DEVICE_CLOSE.execute(job);}
        catch(java.util.concurrent.RejectedExecutionException e){System.err.println("[MPhone] Audio device did not release; close queue is busy.");}
    }
}
