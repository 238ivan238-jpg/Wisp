package app.wisp.voice;

import org.junit.Test;
import java.io.File;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class DictationPipelineTest {
    private File audio() throws Exception {File f=File.createTempFile("wisp-test-",".m4a");f.deleteOnExit();Files.write(f.toPath(),new byte[256]);return f;}
    @Test public void cleanupFailureKeepsTheWholeTranscriptAvailable() throws Exception {
        GroqClient api=new GroqClient((p,m,k,t,b)->p.equals("/audio/transcriptions")
            ?new GroqClient.Response(200,"{\"text\":\"Ну, deploy завтра.\"}")
            :new GroqClient.Response(429,"{}"));
        var result=DictationPipeline.run(api,audio(),"key","auto",false,"","clean",text->assertEquals("Ну, deploy завтра.",text));
        assertEquals("Ну, deploy завтра.",result.text());assertFalse(result.warning().isEmpty());
    }
    @Test public void rawModeDoesNotSendTextToCleanupModel() throws Exception {
        GroqClient api=new GroqClient((p,m,k,t,b)->{assertEquals("/audio/transcriptions",p);return new GroqClient.Response(200,"{\"text\":\"Original.\"}");});
        var result=DictationPipeline.run(api,audio(),"key","en",false,"","raw",t->{throw new AssertionError("No cleanup phase expected");});
        assertEquals("Original.",result.text());assertTrue(result.warning().isEmpty());
    }
    @Test public void cancellationNeverReturnsAFallbackResult() throws Exception {
        GroqClient api=new GroqClient((p,m,k,t,b)->{
            if(p.equals("/audio/transcriptions"))return new GroqClient.Response(200,"{\"text\":\"private\"}");
            throw new InterruptedIOException("cancelled");
        });
        assertThrows(InterruptedIOException.class,()->DictationPipeline.run(api,audio(),"key","auto",false,"","clean",t->{}));
    }
    @Test public void successfulCleanupReturnsEditedTextAndKeepsOriginalInResult() throws Exception {
        GroqClient api=new GroqClient((p,m,k,t,b)->new GroqClient.Response(200,p.equals("/audio/transcriptions")
            ?"{\"text\":\"Эм, готов.\"}":"{\"choices\":[{\"message\":{\"content\":\"Готов.\"}}]}"));
        var result=DictationPipeline.run(api,audio(),"key","ru",false,"","clean",t->{});
        assertEquals("Эм, готов.",result.original());assertEquals("Готов.",result.text());
    }
    @Test public void dictionaryReachesBothStagesAndMultilineMixedLanguageOutputIsPreserved() throws Exception {
        String spoken="список проверить гитхаб потом обновить README и запустить unit tests";
        String edited="Список:\n- Проверить GitHub.\n- Обновить README.\n- Запустить unit tests.";
        java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        GroqClient api=new GroqClient((p,m,k,t,b)->{
            calls.incrementAndGet();String request=new String(b,java.nio.charset.StandardCharsets.UTF_8);
            if(p.equals("/audio/transcriptions")){
                assertTrue(request.contains("name=\"prompt\"\r\n\r\nGitHub\r\n"));
                return new GroqClient.Response(200,new org.json.JSONObject().put("text",spoken).toString());
            }
            assertEquals("/chat/completions",p);
            var data=new org.json.JSONObject(new org.json.JSONObject(request).getJSONArray("messages").getJSONObject(1).getString("content"));
            assertEquals(spoken,data.getString("dictation"));assertEquals("GitHub",data.getString("spelling_hints"));
            return new GroqClient.Response(200,new org.json.JSONObject().put("choices",new org.json.JSONArray().put(new org.json.JSONObject()
                .put("message",new org.json.JSONObject().put("content",edited)))).toString());
        });
        var result=DictationPipeline.run(api,audio(),"key","auto",false,"GitHub","clean",t->assertEquals(spoken,t));
        assertEquals(2,calls.get());assertEquals(spoken,result.original());assertEquals(edited,result.text());assertTrue(result.warning().isEmpty());
    }
}
