package app.wisp.voice;

import android.media.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/** Decode once, cut near quiet gaps, retain every PCM frame exactly once. */
final class AudioChunks implements AutoCloseable {
    static final long SINGLE_MS=75000;
    record Pcm(int rate,int channels) {int frameBytes(){return channels*2;}}
    interface Decoder {Pcm decode(File input,File output)throws Exception;}
    interface Factory {AudioChunks open(File source,File cache,long duration)throws Exception;}
    final List<File> files;
    private final File directory;
    AudioChunks(List<File> files,File directory){this.files=List.copyOf(files);this.directory=directory;}
    static AudioChunks open(File source,File cache,long duration)throws Exception{return open(source,cache,duration,AudioChunks::decode);}
    static AudioChunks open(File source,File cache,long duration,Decoder decoder)throws Exception{
        if(duration<=SINGLE_MS)return new AudioChunks(List.of(source),null);
        File root=new File(cache,"audio-chunks");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Chunk storage unavailable");
        File dir=new File(root,UUID.randomUUID().toString());if(!dir.mkdir())throw new IOException("Chunk storage unavailable");
        try{
            File pcm=new File(dir,"decoded.pcm");Pcm format=decoder.decode(source,pcm);
            if(format.rate<8000||format.rate>96000||format.channels<1||format.channels>2||pcm.length()==0||pcm.length()%format.frameBytes()!=0||pcm.length()>(long)(RecordingLimits.MAX_SECONDS+5)*format.rate*format.frameBytes())throw new IOException("Unsupported PCM");
            List<File> result=new ArrayList<>();long total=pcm.length()/format.frameBytes(),from=0;
            try(RandomAccessFile in=new RandomAccessFile(pcm,"r")){
                while(from<total){interrupted();long remaining=total-from,to;
                    if(remaining<=format.rate*75L)to=total;
                    else{long target=from+format.rate*60L;to=findCut(in,format,from,target);}
                    File part=new File(dir,"part-"+result.size()+".wav");writeWave(in,part,format,from,to);result.add(part);from=to;
                }
            }
            pcm.delete();return new AudioChunks(result,dir);
        }catch(Exception e){delete(dir);if(e instanceof InterruptedIOException)throw e;throw new AudioPreparationException(e);}
    }
    private static long findCut(RandomAccessFile in,Pcm f,long start,long target)throws Exception{
        long scanStart=Math.max(start+f.rate*45L,target-f.rate*10L);int frames=(int)(target-scanStart);
        byte[] data=new byte[frames*f.frameBytes()];in.seek(scanStart*f.frameBytes());in.readFully(data);
        int window=Math.max(1,f.rate/5),step=Math.max(1,f.rate/50);double best=Double.MAX_VALUE;int bestAt=frames-window;boolean quiet=false;
        for(int at=0;at+window<=frames;at+=step){interrupted();double energy=0;
            for(int i=at*f.channels;i<(at+window)*f.channels;i++){int sample=(short)((data[i*2]&255)|(data[i*2+1]<<8));energy+=(double)sample*sample;}
            energy/=window*f.channels;
            if(energy<180*180){quiet=true;bestAt=at;}
            else if(!quiet&&energy<best){best=energy;bestAt=at;}
        }
        return scanStart+bestAt+window/2;
    }
    private static void writeWave(RandomAccessFile input,File file,Pcm f,long from,long to)throws Exception{
        long bytes=(to-from)*f.frameBytes();if(bytes>Integer.MAX_VALUE)throw new IOException("Chunk too large");
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file))){
            out.write(new byte[]{'R','I','F','F'});le(out,36+bytes,4);out.write(new byte[]{'W','A','V','E','f','m','t',' '});le(out,16,4);le(out,1,2);le(out,f.channels,2);le(out,f.rate,4);le(out,(long)f.rate*f.frameBytes(),4);le(out,f.frameBytes(),2);le(out,16,2);out.write(new byte[]{'d','a','t','a'});le(out,bytes,4);
            input.seek(from*f.frameBytes());byte[] buffer=new byte[8192];long left=bytes;
            while(left>0){interrupted();int n=input.read(buffer,0,(int)Math.min(left,buffer.length));if(n<0)throw new EOFException();out.write(buffer,0,n);left-=n;}
        }
    }
    private static void le(OutputStream out,long value,int bytes)throws IOException{for(int i=0;i<bytes;i++)out.write((int)(value>>(i*8))&255);}
    static String fingerprint(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){interrupted();digest.update(b,0,n);}}StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));return hex.toString();}
    private static Pcm decode(File input,File output)throws Exception{
        MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;boolean started=false;
        try{
            extractor.setDataSource(input.getAbsolutePath());MediaFormat format=null;
            for(int i=0;i<extractor.getTrackCount();i++){var candidate=extractor.getTrackFormat(i);String mime=candidate.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("audio/")){format=candidate;extractor.selectTrack(i);break;}}
            if(format==null)throw new IOException("No audio track");
            Pcm pcm=new Pcm(format.getInteger(MediaFormat.KEY_SAMPLE_RATE),format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            codec=MediaCodec.createDecoderByType(Objects.requireNonNull(format.getString(MediaFormat.KEY_MIME)));codec.configure(format,null,null,0);codec.start();started=true;
            boolean inputEnd=false,outputEnd=false;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();long lastOutput=System.nanoTime();
            try(OutputStream out=new BufferedOutputStream(new FileOutputStream(output))){while(!outputEnd){interrupted();
                if(!inputEnd){int slot=codec.dequeueInputBuffer(10000);if(slot>=0){ByteBuffer buffer=Objects.requireNonNull(codec.getInputBuffer(slot));buffer.clear();int n=extractor.readSampleData(buffer,0);
                    if(n<0){codec.queueInputBuffer(slot,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnd=true;}
                    else{codec.queueInputBuffer(slot,0,n,extractor.getSampleTime(),0);extractor.advance();}}}
                int slot=codec.dequeueOutputBuffer(info,10000);
                if(slot==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat actual=codec.getOutputFormat();if(actual.containsKey(MediaFormat.KEY_PCM_ENCODING)&&actual.getInteger(MediaFormat.KEY_PCM_ENCODING)!=AudioFormat.ENCODING_PCM_16BIT)throw new IOException("Unsupported PCM encoding");pcm=new Pcm(actual.getInteger(MediaFormat.KEY_SAMPLE_RATE),actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT));}
                else if(slot>=0){try{if(info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){ByteBuffer buffer=Objects.requireNonNull(codec.getOutputBuffer(slot));buffer.position(info.offset);buffer.limit(info.offset+info.size);byte[] block=new byte[Math.min(8192,info.size)];while(buffer.hasRemaining()){int n=Math.min(block.length,buffer.remaining());buffer.get(block,0,n);out.write(block,0,n);}}lastOutput=System.nanoTime();outputEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;}finally{codec.releaseOutputBuffer(slot,false);}}
                if(System.nanoTime()-lastOutput>15_000_000_000L)throw new IOException("Audio decoder timeout");
            }}return pcm;
        }finally{if(codec!=null){try{if(started)codec.stop();}finally{codec.release();}}extractor.release();}
    }
    private static void interrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
    static void delete(File dir){File[] files=dir.listFiles();if(files!=null)for(File f:files){if(f.isDirectory())delete(f);else f.delete();}dir.delete();}
    @Override public void close(){if(directory!=null)delete(directory);}
}
