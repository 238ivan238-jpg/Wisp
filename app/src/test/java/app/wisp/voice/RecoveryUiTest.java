package app.wisp.voice;

import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.io.File;
import java.nio.file.Files;
import javax.crypto.KeyGenerator;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class RecoveryUiTest {
    private TextView find(View v,String text){if(v instanceof TextView t&&text.contentEquals(t.getText()))return t;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){TextView t=find(g.getChildAt(i),text);if(t!=null)return t;}return null;}
    @Test public void recoveryShowsSavedTextAndCopiesOnlyAfterAnExplicitTap()throws Exception{
        var context=RuntimeEnvironment.getApplication();var key=KeyGenerator.getInstance("AES").generateKey();
        RecoveryStore store=new RecoveryStore(Files.createTempDirectory("recovery-ui").toFile(),()->key);
        File audio=new File(context.getCacheDir(),"fixture.m4a");Files.write(audio.toPath(),new byte[128]);
        var entry=store.create(audio,System.currentTimeMillis(),5000,"clean","auto","",false,false);entry.original="ну личный текст";entry.text="Личный текст.";entry.handed=true;store.save(entry);
        try(var controller=Robolectric.buildActivity(RecoveryActivity.class).create()){
            var activity=controller.get();ReflectionHelpers.setField(activity,"store",store);ReflectionHelpers.setField(activity,"selected",entry.id);controller.start().resume().visible();
            View root=activity.getWindow().getDecorView();assertTrue(root.findViewWithTag("nav_1").isSelected());assertNotNull(find(root,"Личный текст."));
            ClipboardManager clipboard=context.getSystemService(ClipboardManager.class);assertFalse(clipboard.hasPrimaryClip());
            find(root,"Скопировать текст").performClick();assertEquals(entry.text,clipboard.getPrimaryClip().getItemAt(0).getText().toString());
            find(root,"Скопировать исходный текст").performClick();assertEquals(entry.original,clipboard.getPrimaryClip().getItemAt(0).getText().toString());
            assertNull(find(root,"↻ Повторить обработку"));assertFalse(new DiagnosticLog(context).report().contains(entry.original));
            Bundle state=new Bundle();controller.saveInstanceState(state);assertFalse(state.toString().contains(entry.original));assertEquals(entry.id,state.getString("selected"));
        }
    }
    @Test public void emptyRecoveryIsASeparateTabWithNoPermissionsOrAutomaticNetworkCalls(){
        try(var controller=Robolectric.buildActivity(RecoveryActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();assertNotNull(find(root,"Восстановление"));assertNotNull(find(root,"Здесь появятся записи, которые не удалось обработать или вставить."));assertTrue(root.findViewWithTag("nav_1").isSelected());
        }
    }
    @Test public void diagnosticsOpenedForRecoveryFiltersToThatRecording(){
        var context=RuntimeEnvironment.getApplication();DiagnosticLog log=new DiagnosticLog(context);String chosen=DiagnosticLog.session(),other=DiagnosticLog.session();
        log.event(chosen,DiagnosticLog.Stage.TRANSCRIPTION,DiagnosticLog.Outcome.FAILURE,"clean",0,-1,-1);
        log.event(other,DiagnosticLog.Stage.UPDATE_CHECK,DiagnosticLog.Outcome.SUCCESS,"clean",0,-1,-1);
        Intent intent=new Intent(context,DiagnosticsActivity.class).putExtra("session",chosen);
        try(var controller=Robolectric.buildActivity(DiagnosticsActivity.class,intent).setup()){
            View root=controller.get().getWindow().getDecorView();assertNotNull(find(root,"События этой записи"));
            assertFalse(allText(root).contains("UPDATE_CHECK"));assertTrue(allText(root).contains("TRANSCRIPTION"));
            find(root,"Все события").performClick();assertTrue(allText(root).contains("UPDATE_CHECK"));
        }
    }
    private String allText(View v){StringBuilder text=new StringBuilder();if(v instanceof TextView t)text.append(t.getText());if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)text.append(allText(g.getChildAt(i)));return text.toString();}

}
