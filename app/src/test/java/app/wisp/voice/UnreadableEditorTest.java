package app.wisp.voice;

import android.Manifest;
import android.accessibilityservice.InputMethod;
import android.app.KeyguardManager;
import android.os.Binder;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import static org.junit.Assert.*;

/** Exercises Android's actual accessibility input wrapper with an unreadable editor. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class UnreadableEditorTest {
    private static final class Editor implements AutoCloseable {
        final ServiceController<GboardCompanionService> controller;
        final GboardCompanionService service;
        final InputMethod input;
        int reads,commits;
        String committed="";
        Editor()throws Exception{
            var app=RuntimeEnvironment.getApplication();Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO);
            app.getSharedPreferences("wisp_private",0).edit().putString("api_data","fixture-not-a-decryptable-key").commit();
            controller=Robolectric.buildService(GboardCompanionService.class).create();service=controller.get();
            Shadows.shadowOf(service.getSystemService(KeyguardManager.class)).setKeyguardLocked(false);
            android.provider.Settings.Secure.putString(service.getContentResolver(),android.provider.Settings.Secure.DEFAULT_INPUT_METHOD,"com.google.android.inputmethod.latin/.LatinIME");
            input=service.onCreateInputMethod();ReflectionHelpers.setField(service,"mInputMethod",input);
            Class<?> remoteInterface=Class.forName("com.android.internal.inputmethod.IRemoteAccessibilityInputConnection");
            Object remote=Proxy.newProxyInstance(remoteInterface.getClassLoader(),new Class<?>[]{remoteInterface},(proxy,method,args)->{
                if(method.getName().equals("asBinder"))return new Binder();
                if(method.getName().equals("getSurroundingText")){reads++;((CompletableFuture<?>)args[args.length-1]).complete(null);}
                if(method.getName().equals("commitText")){commits++;committed=args[1].toString();assertEquals(1,args[2]);}
                return null;
            });
            Class<?> group=Class.forName("com.android.internal.inputmethod.CancellationGroup");
            Object connection=Class.forName("com.android.internal.inputmethod.RemoteAccessibilityInputConnection").getConstructor(remoteInterface,group).newInstance(remote,group.getConstructor().newInstance());
            ReflectionHelpers.setField(input,"mStartedInputConnection",connection);
            service.onServiceConnected();EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;info.packageName="example.custom.editor";input.onStartInput(info,false);
        }
        void deliver(DictationPipeline.Result result)throws Exception{
            var method=GboardCompanionService.class.getDeclaredMethod("deliver",DictationPipeline.Result.class,long.class,String.class,String.class);method.setAccessible(true);
            method.invoke(service,result,System.currentTimeMillis(),"translate",DiagnosticLog.session());
        }
        public void close(){controller.destroy();}
    }
    @Test public void neitherRecordingActionRequiresReadingSurroundingText()throws Exception{
        for(boolean english:new boolean[]{false,true})try(var editor=new Editor()){
            VoiceControls controls=ReflectionHelpers.getField(editor.service,"bubble");controls.getChildAt(english?1:0).performClick();
            assertNotNull(ReflectionHelpers.getField(editor.service,"recorder"));assertEquals(0,editor.reads);
            assertEquals(english?"translate":"clean",ReflectionHelpers.getField(editor.service,"mode"));
        }
    }
    @Test public void actualAccessibilityWrapperSendsOnceAndPreservesWarningsWithoutClaimingConfirmedWords()throws Exception{
        try(var editor=new Editor()){
            String text="Tasks:\n- Review the API. 🙏\n- Keep the budget.";
            String warning="Перевод не удался. Сохранён исходный текст.";
            editor.deliver(new DictationPipeline.Result(text,text,warning));
            assertEquals(text,editor.committed);assertEquals(1,editor.commits);assertEquals(1,editor.reads);
            assertEquals(0,new UsageStats(editor.service).snapshot().words());
            TextView label=ReflectionHelpers.getField(editor.service,"label");assertEquals(warning,label.getText().toString());
            String report=new DiagnosticLog(editor.service).report();assertTrue(report.contains("CONTEXT_UNAVAILABLE"));assertTrue(report.contains("UNCONFIRMED"));assertFalse(report.contains(text));
        }
    }
    @Test public void aClosedEditorStillPreventsAnyInsertion()throws Exception{
        try(var editor=new Editor()){
            editor.input.onFinishInput();editor.deliver(new DictationPipeline.Result("private","private",""));assertEquals(0,editor.commits);
        }
    }
}
