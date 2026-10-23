package app.wisp.voice;

import org.json.*;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class TranslationTest {
    private File audio()throws Exception{File f=File.createTempFile("wisp-translation-",".m4a");f.deleteOnExit();Files.write(f.toPath(),new byte[256]);return f;}
    private GroqClient.Response answer(String text)throws Exception{return new GroqClient.Response(200,new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("finish_reason","stop").put("message",new JSONObject().put("content",text)))).toString());}
    @Test public void pipelineUsesTranslationTaskAndPreservesDictionaryAndMultilineOutput()throws Exception{
        String source="список проверь GitHub и не меняй API";
        String translated="Checklist:\n- Check GitHub.\n- Don't change the API.";AtomicInteger calls=new AtomicInteger();
        GroqClient api=new GroqClient((p,m,k,t,b)->{
            calls.incrementAndGet();
            if(p.equals("/audio/transcriptions"))return new GroqClient.Response(200,new JSONObject().put("text",source).toString());
            JSONObject body=new JSONObject(new String(b,StandardCharsets.UTF_8));JSONArray messages=body.getJSONArray("messages");
            assertEquals(GroqClient.TRANSLATION_PROMPT,messages.getJSONObject(0).getString("content"));
            JSONObject data=new JSONObject(messages.getJSONObject(1).getString("content"));
            assertEquals(source,data.getString("dictation"));assertEquals("GitHub",data.getString("spelling_hints"));return answer(translated);
        });
        var result=DictationPipeline.run(api,audio(),"key","auto",false,"GitHub","translate",original->assertEquals(source,original));
        assertEquals(2,calls.get());assertEquals(source,result.original());assertEquals(translated,result.text());assertTrue(result.warning().isEmpty());
    }
    @Test public void fallbackModelRetainsTranslationTaskInsteadOfRevertingToCleanup()throws Exception{
        AtomicInteger chats=new AtomicInteger();GroqClient api=new GroqClient((p,m,k,t,b)->{
            if(p.equals("/models"))return new GroqClient.Response(200,"{\"data\":[{\"id\":\"llama-3.3-70b-versatile\"}]}");
            JSONObject body=new JSONObject(new String(b,StandardCharsets.UTF_8));
            assertEquals(GroqClient.TRANSLATION_PROMPT,body.getJSONArray("messages").getJSONObject(0).getString("content"));
            if(chats.incrementAndGet()==1)return new GroqClient.Response(404,"{\"error\":{\"code\":\"model_not_found\"}}");
            assertEquals("llama-3.3-70b-versatile",body.getString("model"));return answer("Don't change the API.");
        });assertEquals("Don't change the API.",api.translate("Не меняй API.","key","API"));assertEquals(2,chats.get());
    }
    @Test public void translationFailureKeepsOriginalAndReportsFailureCategory()throws Exception{
        GroqClient api=new GroqClient((p,m,k,t,b)->p.equals("/audio/transcriptions")?new GroqClient.Response(200,"{\"text\":\"Не меняй API.\"}"):new GroqClient.Response(429,"{}"));
        var result=DictationPipeline.run(api,audio(),"key","auto",false,"","translate",s->{});
        assertEquals("Не меняй API.",result.text());assertTrue(result.warning().startsWith("Перевод не удался."));assertEquals("limit",result.errorCategory());
    }
    @Test public void cancelledTranslationDoesNotBecomeAnOrdinaryFallback()throws Exception{
        GroqClient api=new GroqClient((p,m,k,t,b)->{if(p.equals("/audio/transcriptions"))return new GroqClient.Response(200,"{\"text\":\"Привет.\"}");throw new InterruptedIOException();});
        assertThrows(InterruptedIOException.class,()->DictationPipeline.run(api,audio(),"key","auto",false,"","translate",s->{}));
    }
    @Test public void errorCategoriesNeverContainProviderMessagesOrCredentials(){
        assertEquals("auth",GroqClient.failureCategory(new GroqClient.ApiException(401,"",false,"private-key")));
        assertEquals("model",GroqClient.failureCategory(new GroqClient.ApiException(403,"",true,"private-dictation")));
        assertEquals("timeout",GroqClient.failureCategory(new java.net.SocketTimeoutException("private")));
        assertEquals("tls",GroqClient.failureCategory(new javax.net.ssl.SSLException("private")));
    }
}
