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
    @Test public void successfulCleanupPreservesOriginalForPreviewToggle() throws Exception {
        GroqClient api=new GroqClient((p,m,k,t,b)->new GroqClient.Response(200,p.equals("/audio/transcriptions")
            ?"{\"text\":\"Эм, готов.\"}":"{\"choices\":[{\"message\":{\"content\":\"Готов.\"}}]}"));
        var result=DictationPipeline.run(api,audio(),"key","ru",false,"","clean",t->{});
        assertEquals("Эм, готов.",result.original());assertEquals("Готов.",result.text());
    }
}
