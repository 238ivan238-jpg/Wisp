package app.wisp.voice;

import org.json.*;
import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=35)
public class EmojiTest {
    private File audio()throws Exception{
        File f=File.createTempFile("wisp-emoji-",".m4a");f.deleteOnExit();Files.write(f.toPath(),new byte[256]);return f;
    }
    private GroqClient.Response answer(String text)throws Exception{
        return new GroqClient.Response(200,new JSONObject().put("choices",new JSONArray().put(new JSONObject()
            .put("finish_reason","stop").put("message",new JSONObject().put("content",text)))).toString());
    }
    @Test public void enabledStyleReachesBothEditingTasksAndPreservesUnicodeListsAndDictionary()throws Exception{
        String[][] examples={
            {"на завтра список проверить GitHub потом купить продукты и забрать посылку","На завтра:\n- Проверить GitHub.\n- Купить продукты 🛒.\n- 📦 Забрать посылку.","For tomorrow:\n- Review GitHub.\n- Buy groceries 🛒.\n- 📦 Pick up the parcel."},
            {"завтра едем на море а вечером вернёмся домой","Завтра едем на море 🌊, а вечером вернёмся домой.","Tomorrow we're going to the seaside 🌊, and we'll be back home in the evening."},
            {"ура завтра отпуск а сегодня закончу работу","🎉 Ура, завтра отпуск! А сегодня закончу работу.","🎉 Hooray, my vacation starts tomorrow! I'll finish work today."}
        };
        for(String mode:new String[]{"clean","translate"})for(String[] example:examples){
            String source=example[0],output=example["translate".equals(mode)?2:1];
            AtomicInteger calls=new AtomicInteger();
            GroqClient api=new GroqClient((p,m,k,t,b)->{
                calls.incrementAndGet();String request=new String(b,StandardCharsets.UTF_8);
                if(p.equals("/audio/transcriptions")){
                    assertFalse(request.contains("Emoji styling"));
                    return new GroqClient.Response(200,new JSONObject().put("text",source).toString());
                }
                JSONArray messages=new JSONObject(request).getJSONArray("messages");
                assertEquals(("translate".equals(mode)?GroqClient.TRANSLATION_PROMPT:GroqClient.PROMPT)+GroqClient.EMOJI_PROMPT,messages.getJSONObject(0).getString("content"));
                JSONObject data=new JSONObject(messages.getJSONObject(1).getString("content"));
                assertEquals(source,data.getString("dictation"));assertEquals("GitHub",data.getString("spelling_hints"));
                return answer(output);
            });
            var result=DictationPipeline.run(api,audio(),"key","auto",false,"GitHub",mode,true,s->assertEquals(source,s));
            assertEquals(2,calls.get());assertEquals(source,result.original());assertEquals(output,result.text());assertTrue(result.warning().isEmpty());
        }
    }
    @Test public void disabledStyleDoesNotReachTheModelInEitherTask()throws Exception{
        for(String mode:new String[]{"clean","translate"}){
            GroqClient api=new GroqClient((p,m,k,t,b)->{
                if(p.equals("/audio/transcriptions"))return new GroqClient.Response(200,"{\"text\":\"Спасибо.\"}");
                String prompt=new JSONObject(new String(b,StandardCharsets.UTF_8)).getJSONArray("messages").getJSONObject(0).getString("content");
                assertEquals("translate".equals(mode)?GroqClient.TRANSLATION_PROMPT:GroqClient.PROMPT,prompt);
                return answer("Thanks.");
            });
            assertEquals("Thanks.",DictationPipeline.run(api,audio(),"key","auto",false,"",mode,false,s->{}).text());
        }
    }
    @Test public void modelFallbackKeepsEmojiStyleAndTheSelectedTask()throws Exception{
        for(String mode:new String[]{"clean","translate"}){
            AtomicInteger chats=new AtomicInteger();
            GroqClient api=new GroqClient((p,m,k,t,b)->{
                if(p.equals("/audio/transcriptions"))return new GroqClient.Response(200,"{\"text\":\"Спасибо.\"}");
                if(p.equals("/models"))return new GroqClient.Response(200,"{\"data\":[{\"id\":\"llama-3.3-70b-versatile\"}]}");
                JSONObject body=new JSONObject(new String(b,StandardCharsets.UTF_8));
                assertEquals(("translate".equals(mode)?GroqClient.TRANSLATION_PROMPT:GroqClient.PROMPT)+GroqClient.EMOJI_PROMPT,body.getJSONArray("messages").getJSONObject(0).getString("content"));
                if(chats.incrementAndGet()==1)return new GroqClient.Response(404,"{\"error\":{\"code\":\"model_not_found\"}}");
                assertEquals("llama-3.3-70b-versatile",body.getString("model"));return answer("🙏 Thanks!");
            });
            assertEquals("🙏 Thanks!",DictationPipeline.run(api,audio(),"key","auto",false,"",mode,true,s->{}).text());assertEquals(2,chats.get());
        }
    }
    @Test public void processingFailureReturnsOriginalWithoutAnExtraEmojiRequest()throws Exception{
        AtomicInteger calls=new AtomicInteger();
        GroqClient api=new GroqClient((p,m,k,t,b)->{calls.incrementAndGet();return p.equals("/audio/transcriptions")
            ?new GroqClient.Response(200,"{\"text\":\"Спасибо.\"}"):new GroqClient.Response(429,"{}");});
        var result=DictationPipeline.run(api,audio(),"key","auto",false,"","clean",true,s->{});
        assertEquals("Спасибо.",result.text());assertEquals("limit",result.errorCategory());assertFalse(result.warning().isEmpty());assertEquals(2,calls.get());
    }
    @Test public void rawModeNeverUsesEmojiProcessingEvenIfCalledWithStaleEnabledFlag()throws Exception{
        GroqClient api=new GroqClient((p,m,k,t,b)->{assertEquals("/audio/transcriptions",p);return new GroqClient.Response(200,"{\"text\":\"Спасибо.\"}");});
        var result=DictationPipeline.run(api,audio(),"key","auto",false,"","raw",true,s->{throw new AssertionError("Raw mode must not process text");});
        assertEquals("Спасибо.",result.text());assertTrue(result.warning().isEmpty());
    }
    @Test public void tailIsRemovedInBothTasksIncludingModelFallbackWithoutAnExtraChat()throws Exception{
        for(String mode:new String[]{"clean","translate"})for(boolean fallback:new boolean[]{false,true}){
            AtomicInteger chats=new AtomicInteger();GroqClient api=new GroqClient((p,m,k,t,b)->{
                if(p.equals("/models"))return new GroqClient.Response(200,"{\"data\":[{\"id\":\"llama-3.3-70b-versatile\"}]}");
                if(chats.incrementAndGet()==1&&fallback)return new GroqClient.Response(404,"{\"error\":{\"code\":\"model_not_found\"}}");
                return answer("Отпуск 🎉 начнётся завтра. Не меняем budget! 😊🥰🔥❤️👍");
            });
            String result="translate".equals(mode)?api.translate("отпуск завтра budget не меняем","fixture","",true):api.clean("отпуск завтра budget не меняем","fixture","",true);
            assertEquals("Отпуск 🎉 начнётся завтра. Не меняем budget!",result);assertEquals(fallback?2:1,chats.get());
        }
    }
    @Test public void disabledStyleAndFailedCleanupNeverFilterTheOriginalUserText()throws Exception{
        String original="Спасибо! 🙏🙏🙏";GroqClient disabled=new GroqClient((p,m,k,t,b)->answer(original));
        assertEquals(original,disabled.clean(original,"fixture","",false));
        GroqClient failed=new GroqClient((p,m,k,t,b)->p.equals("/audio/transcriptions")?new GroqClient.Response(200,new JSONObject().put("text",original).toString()):new GroqClient.Response(403,"{}"));
        assertEquals(original,DictationPipeline.run(failed,audio(),"fixture","auto",false,"","clean",true,s->{}).text());
    }
    @Test public void anEmojiOnlyBadAnswerFallsBackToOriginalInsteadOfInsertingNothing()throws Exception{
        GroqClient api=new GroqClient((p,m,k,t,b)->p.equals("/audio/transcriptions")?new GroqClient.Response(200,"{\"text\":\"Не меняем бюджет.\"}"):answer("😊🎉👍"));
        var result=DictationPipeline.run(api,audio(),"fixture","auto",false,"","clean",true,s->{});
        assertEquals("Не меняем бюджет.",result.text());assertFalse(result.warning().isEmpty());
    }
    @Test public void tailRemovalIsLoggedWithoutLoggingText()throws Exception{
        var context=org.robolectric.RuntimeEnvironment.getApplication();DiagnosticLog log=new DiagnosticLog(context);
        GroqClient api=new GroqClient((p,m,k,t,b)->answer("Личная фраза. 😊🎉"),log,DiagnosticLog.session(),"clean");
        assertEquals("Личная фраза.",api.clean("личная фраза","fixture","",true));String report=log.report();
        assertTrue(report.contains("EMOJI_TAIL_REMOVED"));assertFalse(report.contains("Личная фраза"));
    }

    @Test public void everyIntensityReachesBothTasksWithoutAllowingATail()throws Exception{
        for(var style:EmojiStyle.values())for(boolean translate:new boolean[]{false,true}){
            AtomicInteger calls=new AtomicInteger();GroqClient client=new GroqClient((p,m,k,t,b)->{calls.incrementAndGet();String prompt=new JSONObject(new String(b,StandardCharsets.UTF_8)).getJSONArray("messages").getJSONObject(0).getString("content");assertTrue(prompt.contains(GroqClient.EMOJI_PROMPT));assertTrue(prompt.contains(style.instruction));return answer("🎉 Отпуск начнётся завтра. 😊😊😊");});
            String text=translate?client.translate("отпуск завтра","fixture","",true,style):client.clean("отпуск завтра","fixture","",true,style);
            assertEquals("🎉 Отпуск начнётся завтра.",text);assertEquals(1,calls.get());
        }
    }

}
