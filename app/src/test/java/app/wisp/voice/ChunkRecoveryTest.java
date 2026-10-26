package app.wisp.voice;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import javax.crypto.KeyGenerator;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class ChunkRecoveryTest {
    private RecoveryStore store;private File cache;private RecoveryStore.Entry entry;
    @Before public void setup()throws Exception{cache=RuntimeEnvironment.getApplication().getCacheDir();var key=KeyGenerator.getInstance("AES").generateKey();store=new RecoveryStore(Files.createTempDirectory("chunk-recovery").toFile(),()->key);File audio=new File(cache,"fixture.m4a");Files.write(audio.toPath(),new byte[256]);entry=store.create(audio,System.currentTimeMillis(),160000,"clean","auto","",false,false);}
    private AudioChunks.Factory factory(){return (source,cache,duration)->{File dir=Files.createTempDirectory(cache.toPath(),"test-parts-").toFile();List<File> parts=new ArrayList<>();for(int i=0;i<3;i++){File file=new File(dir,i+".wav");byte[] bytes=new byte[256];Arrays.fill(bytes,(byte)i);Files.write(file.toPath(),bytes);parts.add(file);}return new AudioChunks(parts,dir);};}
    @Test public void failedSecondPartResumesWithoutReuploadAndFinalEditorGetsTheWholeContext()throws Exception{
        List<String> calls=new ArrayList<>();GroqClient first=new GroqClient((path,method,key,type,body)->{calls.add(path);if(calls.size()==1)return new GroqClient.Response(200,"{\"text\":\"Первый пункт: проверить API.\"}");return new GroqClient.Response(403,"{}");});
        assertThrows(GroqClient.ApiException.class,()->RecoveryJob.run(store,entry,cache,first,"fixture",new RecoveryJob.Observer(){},factory()));
        var saved=store.get(entry.id);assertEquals(1,saved.parts.size());assertTrue(saved.original.isEmpty());assertEquals("Первый пункт: проверить API.",saved.availableText());
        int[] asr={0},editing={0};List<String> stages=new ArrayList<>();GroqClient next=new GroqClient((path,m,k,t,body)->{
            if(path.contains("audio")){asr[0]++;return new GroqClient.Response(200,asr[0]==1?"{\"text\":\"Второй пункт: бюджет не менять.\"}":"{\"text\":\"Третий пункт: закрыть задачу.\"}");}
            editing[0]++;String request=new String(body,java.nio.charset.StandardCharsets.UTF_8);assertTrue(request.contains("Первый пункт"));assertTrue(request.contains("бюджет не менять"));assertTrue(request.contains("Третий пункт"));
            return new GroqClient.Response(200,"{\"choices\":[{\"message\":{\"content\":\"1. Проверить API.\\n2. Бюджет не менять.\\n3. Закрыть задачу.\"},\"finish_reason\":\"stop\"}]}");});
        var result=RecoveryJob.run(store,saved,cache,next,"fixture",new RecoveryJob.Observer(){public void progress(JobProgress p){stages.add(p.label());}},factory());
        assertEquals(2,asr[0]);assertEquals(1,editing[0]);assertTrue(result.original().contains("Первый пункт"));assertTrue(stages.contains("Распознаю часть 2 из 3…"));assertTrue(stages.contains("Исправляю текст…"));assertEquals(3,store.get(entry.id).parts.size());
        assertFalse(new DiagnosticLog(RuntimeEnvironment.getApplication()).report().contains("Первый пункт"));
    }
    @Test public void changedPartPlanIsRejectedWithoutDestroyingTheCheckpoint()throws Exception{
        entry.parts.add(new RecoveryStore.Entry.Part("different-hash","Already recognized."));store.save(entry);
        GroqClient api=new GroqClient((p,m,k,t,b)->{throw new AssertionError("Must not mix different audio plans");});assertThrows(IOException.class,()->RecoveryJob.run(store,entry,cache,api,"fixture",new RecoveryJob.Observer(){},factory()));assertEquals("Already recognized.",store.get(entry.id).availableText());
    }
    @Test public void metricsAreNotCountedAgainWhenCleanupIsRetried()throws Exception{
        entry.original="Один два три";entry.recognitionMs=123;store.save(entry);int[] speech={0},recognition={0},editing={0};
        RecoveryJob.Observer observer=new RecoveryJob.Observer(){public void speech(RecoveryStore.Entry e){speech[0]++;}public void timing(RecoveryStore.Entry e,UsageStats.WaitStage s,long ms){if(s==UsageStats.WaitStage.RECOGNITION)recognition[0]++;else editing[0]++;}};
        GroqClient fail=new GroqClient((p,m,k,t,b)->new GroqClient.Response(403,"{}"));RecoveryJob.run(store,entry,cache,fail,"fixture",observer,factory());
        GroqClient success=new GroqClient((p,m,k,t,b)->new GroqClient.Response(200,"{\"choices\":[{\"message\":{\"content\":\"Один, два, три.\"},\"finish_reason\":\"stop\"}]}"));RecoveryJob.run(store,store.get(entry.id),cache,success,"fixture",observer,factory());
        assertEquals(1,speech[0]);assertEquals(1,recognition[0]);assertEquals(1,editing[0]);
    }
    @Test public void storedIntensityIsUsedOnRetryEvenIfPreferencesChange()throws Exception{
        entry.style=EmojiStyle.ACTIVE;entry.original="ура завтра отпуск";store.save(entry);SecureSettings settings=new SecureSettings(RuntimeEnvironment.getApplication());settings.setEmojiStyle(EmojiStyle.RESTRAINED);
        entry=store.get(entry.id);assertEquals(EmojiStyle.ACTIVE,entry.style);
    }

}
