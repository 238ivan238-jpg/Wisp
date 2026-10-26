package app.wisp.voice;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class AudioChunksTest {
    private File cache,source;
    @Before public void setup()throws Exception{cache=Files.createTempDirectory("audio-split-test").toFile();source=new File(cache,"recording.m4a");Files.write(source.toPath(),new byte[128]);}
    static byte[] pcm(int seconds,int channels){
        ByteBuffer data=ByteBuffer.allocate(seconds*8000*channels*2).order(ByteOrder.LITTLE_ENDIAN);
        for(int frame=0;frame<seconds*8000;frame++){double t=frame/8000d;short sample=(short)((t>=55&&t<56||t>=112&&t<113)?0:Math.sin(frame*.13)*12000);for(int c=0;c<channels;c++)data.putShort(sample);}
        return data.array();
    }
    @Test public void splitPreservesEveryFrameExactlyOnceAndUsesQuietGaps()throws Exception{
        byte[] original=pcm(160,1);List<File> files;
        try(AudioChunks chunks=AudioChunks.open(source,cache,160000,(in,out)->{Files.write(out.toPath(),original);return new AudioChunks.Pcm(8000,1);})){files=chunks.files;assertEquals(3,files.size());ByteArrayOutputStream combined=new ByteArrayOutputStream();
            for(File file:files){byte[] wav=Files.readAllBytes(file.toPath());assertEquals("RIFF",new String(wav,0,4,java.nio.charset.StandardCharsets.US_ASCII));ByteBuffer header=ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);assertEquals(8000,header.getInt(24));assertEquals(wav.length-44,header.getInt(40));assertTrue((wav.length-44)<=75*8000*2);combined.write(wav,44,wav.length-44);}
            assertArrayEquals(original,combined.toByteArray());double first=(files.get(0).length()-44)/(8000d*2);assertTrue(first>=55&&first<=56);
        }
        for(File file:files)assertFalse(file.exists());assertTrue(source.exists());
    }
    @Test public void stereoAndContinuousSpeechRemainFrameAlignedWithoutOverlap()throws Exception{
        byte[] original=pcm(155,2);try(AudioChunks chunks=AudioChunks.open(source,cache,155000,(in,out)->{Files.write(out.toPath(),original);return new AudioChunks.Pcm(8000,2);})){ByteArrayOutputStream all=new ByteArrayOutputStream();for(File file:chunks.files){byte[] bytes=Files.readAllBytes(file.toPath());assertEquals(0,(bytes.length-44)%4);all.write(bytes,44,bytes.length-44);}assertArrayEquals(original,all.toByteArray());}
    }
    @Test public void shortAudioIsNotDecodedAndOriginalIsNeverDeleted()throws Exception{
        try(AudioChunks chunks=AudioChunks.open(source,cache,5000,(in,out)->{throw new AssertionError("Short audio must not be decoded");})){assertEquals(List.of(source),chunks.files);}assertTrue(source.exists());
    }
    @Test public void decoderFailuresAndCancellationCleanOnlyTemporaryData()throws Exception{
        assertThrows(AudioPreparationException.class,()->AudioChunks.open(source,cache,160000,(in,out)->{Files.write(out.toPath(),new byte[400]);throw new IOException("bad codec");}));assertEquals(0,Objects.requireNonNull(new File(cache,"audio-chunks").list()).length);assertTrue(source.exists());
        assertThrows(InterruptedIOException.class,()->AudioChunks.open(source,cache,160000,(in,out)->{throw new InterruptedIOException();}));assertTrue(source.exists());
    }
    @Test public void unsupportedPcmIsRejectedRatherThanUploadedAsDamagedSound(){assertThrows(AudioPreparationException.class,()->AudioChunks.open(source,cache,160000,(in,out)->{Files.write(out.toPath(),new byte[201]);return new AudioChunks.Pcm(8000,2);}));}
}
