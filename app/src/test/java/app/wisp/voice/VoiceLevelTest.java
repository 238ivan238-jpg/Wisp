package app.wisp.voice;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceLevelTest {
    @Test public void silenceAndLowNoiseStayAtDotsWhileSpeechGrowsMonotonically(){
        assertEquals(0,VoiceLevel.normalize(0),0);assertEquals(0,VoiceLevel.normalize(80),0);
        assertTrue(VoiceLevel.normalize(400)>0);assertTrue(VoiceLevel.normalize(2000)>VoiceLevel.normalize(400));
        assertTrue(VoiceLevel.normalize(12000)>VoiceLevel.normalize(2000));assertEquals(1,VoiceLevel.normalize(32767),0);
        assertEquals(0,VoiceLevel.normalize(-1),0);assertEquals(1,VoiceLevel.normalize(Integer.MAX_VALUE),0);
    }
    @Test public void quietSpeechMovesLessThanLoudSpeechWithoutGeneratingItsOwnPulses(){
        VoiceLevel quiet=new VoiceLevel(),loud=new VoiceLevel();
        for(int t=0;t<=300;t+=100){quiet.sample(400,t);loud.sample(15000,t);}
        quiet.advance(400);loud.advance(400);
        for(int i=0;i<3;i++)assertTrue(quiet.level(i)<loud.level(i));
        VoiceLevel silence=new VoiceLevel();for(int t=0;t<5000;t+=16)silence.advance(t);
        for(int i=0;i<3;i++)assertEquals(0,silence.level(i),0);assertFalse(silence.moving());
    }
    @Test public void barsFollowRecentDifferentPeaksRatherThanSynchronizedRandomHeights(){
        VoiceLevel level=new VoiceLevel();level.sample(400,0);level.sample(1200,100);level.sample(20000,200);level.snap();
        assertTrue(level.level(1)>level.level(0));assertTrue(level.level(0)>level.level(2));
        assertEquals(VoiceLevel.normalize(1200)*.82f,level.level(0),.001f);
    }
    @Test public void attackIsQuickAndReleaseSmoothThenComesToRest(){
        VoiceLevel level=new VoiceLevel();level.sample(32767,0);level.advance(45);assertTrue(level.level(1)>.6f);
        level.sample(0,45);level.advance(90);assertTrue(level.level(1)>.4f);assertTrue(level.level(1)<.64f);
        for(int t=100;t<3000;t+=100)level.sample(0,t);level.advance(3100);
        for(int i=0;i<3;i++)assertEquals(0,level.level(i),0);assertFalse(level.moving());
    }
    @Test public void resetClearsHistorySoANewRecordingOrPauseCannotShowOldPeaks(){
        VoiceLevel level=new VoiceLevel();for(int t=0;t<500;t+=100)level.sample(20000,t);level.snap();assertTrue(level.level(1)>.9f);
        level.reset();assertFalse(level.moving());for(int i=0;i<3;i++)assertEquals(0,level.level(i),0);
        level.sample(0,5000);level.advance(6000);for(int i=0;i<3;i++)assertEquals(0,level.level(i),0);
    }
}
