package app.wisp.voice;

import org.json.JSONObject;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class CleanupCompatibilityTest {
    private static final String COMPLETE="{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Завтра deploy.\"}}]}";
    private static final String MISSING="{\"error\":{\"code\":\"model_not_found\",\"message\":\"The model does not exist or you do not have access to it.\"}}";
    @Test public void defaultRequestMatchesCurrentGroqSdkParameters() throws Exception {
        GroqClient c=new GroqClient((path,method,key,type,bytes)->{
            assertEquals("/chat/completions",path);
            JSONObject body=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            assertEquals("openai/gpt-oss-20b",body.getString("model"));
            assertEquals("low",body.getString("reasoning_effort"));
            assertEquals(4096,body.getInt("max_completion_tokens"));assertFalse(body.has("max_tokens"));
            assertEquals("Ну, завтра deploy.",new JSONObject(body.getJSONArray("messages").getJSONObject(1).getString("content")).getString("dictation"));
            return new GroqClient.Response(200,COMPLETE);
        });assertEquals("Завтра deploy.",c.clean("Ну, завтра deploy.","key"));
    }
    @Test public void unavailableModelUsesAnAdvertisedCompatibleFallback() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        GroqClient c=new GroqClient((path,method,key,type,bytes)->{
            int call=calls.incrementAndGet();
            if(call==1){assertEquals("/chat/completions",path);return new GroqClient.Response(404,MISSING);}
            if(call==2){assertEquals("/models",path);assertEquals("GET",method);
                return new GroqClient.Response(200,"{\"data\":[{\"id\":\"whisper-large-v3\"},{\"id\":\"llama-3.3-70b-versatile\"}]}");}
            assertEquals(3,call);JSONObject body=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            assertEquals("llama-3.3-70b-versatile",body.getString("model"));assertFalse(body.has("reasoning_effort"));
            JSONObject data=new JSONObject(body.getJSONArray("messages").getJSONObject(1).getString("content"));
            assertEquals("Ну, завтра deploy.",data.getString("dictation"));assertEquals("GitHub",data.getString("spelling_hints"));
            return new GroqClient.Response(200,COMPLETE);
        });assertEquals("Завтра deploy.",c.clean("Ну, завтра deploy.","key","clean","GitHub"));assertEquals(3,calls.get());
    }
    @Test public void decommissionedModelWith400CanFallBack() throws Exception {
        AtomicInteger chats=new AtomicInteger();
        GroqClient c=new GroqClient((path,method,key,type,bytes)->{
            if(path.equals("/models"))return new GroqClient.Response(200,"{\"data\":[{\"id\":\"llama-3.1-8b-instant\"}]}");
            if(chats.incrementAndGet()==1)return new GroqClient.Response(400,"{\"error\":{\"code\":\"model_decommissioned\"}}");
            return new GroqClient.Response(200,COMPLETE);
        });assertEquals("Завтра deploy.",c.clean("raw","key"));assertEquals(2,chats.get());
    }
    @Test public void authorizationRateLimitsAndGenericForbiddenAreNeverRetried() {
        for(int status:new int[]{401,403,429}){
            AtomicInteger calls=new AtomicInteger();
            GroqClient c=new GroqClient((p,m,k,t,b)->{calls.incrementAndGet();return new GroqClient.Response(status,"{}");});
            GroqClient.ApiException e=assertThrows(GroqClient.ApiException.class,()->c.clean("raw","key"));
            assertEquals(status,e.status);assertFalse(e.unavailableModel);assertEquals(1,calls.get());
        }
    }
    @Test public void genericBadParameterIsNotRetriedAsAModelProblem() {
        AtomicInteger calls=new AtomicInteger();
        GroqClient c=new GroqClient((p,m,k,t,b)->{calls.incrementAndGet();return new GroqClient.Response(400,
            "{\"error\":{\"code\":\"invalid_request_error\",\"message\":\"private dictation and key should never be displayed\"}}");});
        GroqClient.ApiException e=assertThrows(GroqClient.ApiException.class,()->c.clean("raw","key"));
        assertEquals(1,calls.get());assertTrue(e.getMessage().contains("HTTP 400"));assertTrue(e.getMessage().contains("invalid_request_error"));
        assertFalse(e.getMessage().contains("private dictation"));
    }
    @Test public void absenceOfSupportedTextModelsDoesNotUseWhisperForChat() {
        AtomicInteger calls=new AtomicInteger();
        GroqClient c=new GroqClient((p,m,k,t,b)->{
            calls.incrementAndGet();return p.equals("/models")?new GroqClient.Response(200,"{\"data\":[{\"id\":\"whisper-large-v3\"}]}")
                :new GroqClient.Response(404,MISSING);
        });
        GroqClient.ApiException e=assertThrows(GroqClient.ApiException.class,()->c.clean("raw","key"));
        assertEquals(2,calls.get());assertTrue(e.getMessage().contains("Без ИИ"));
    }
    @Test public void rateLimitedFallbackStopsWithoutTryingFurtherModels() {
        AtomicInteger chats=new AtomicInteger();
        GroqClient c=new GroqClient((p,m,k,t,b)->{
            if(p.equals("/models"))return new GroqClient.Response(200,"{\"data\":[{\"id\":\"llama-3.3-70b-versatile\"},{\"id\":\"llama-3.1-8b-instant\"}]}");
            return chats.incrementAndGet()==1?new GroqClient.Response(404,MISSING):new GroqClient.Response(429,"{}");
        });
        assertEquals(429,assertThrows(GroqClient.ApiException.class,()->c.clean("raw","key")).status);assertEquals(2,chats.get());
    }
    @Test public void malformedErrorCodeCannotExposeCredentials() {
        GroqClient c=new GroqClient((p,m,k,t,b)->new GroqClient.Response(400,"{\"error\":{\"code\":\"gsk_private_secret\",\"message\":\"key: gsk_private_secret\"}}"));
        String message=assertThrows(GroqClient.ApiException.class,()->c.clean("raw","gsk_private_secret")).getMessage();
        assertFalse(message.contains("gsk_private_secret"));assertTrue(message.contains("HTTP 400"));
    }
}
