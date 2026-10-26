package app.wisp.voice;

import android.content.*;
import android.os.*;
import android.view.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class NewControlsTest {
    @Test public void emojiIntensitySurvivesDisableAndUpgradeDefaultsToModerate(){
        var context=RuntimeEnvironment.getApplication();SecureSettings s=new SecureSettings(context);assertEquals(EmojiStyle.MODERATE,s.emojiStyle());s.setEmojiStyle(EmojiStyle.ACTIVE);s.setEmojis(true);s.setMode("raw");assertFalse(s.emojis());assertEquals(EmojiStyle.ACTIVE,new SecureSettings(context).emojiStyle());assertEquals(EmojiStyle.MODERATE,EmojiStyle.parse("future-unknown"));
    }
    @Test public void speedUsesMatchedSpeechTimeAndPeriodFiltersWaits(){
        UsageStats s=new UsageStats(RuntimeEnvironment.getApplication());long now=System.currentTimeMillis();s.recordSpeech(now,"one two three four",2000);s.recordUse(now,60000);s.recordWait(now,UsageStats.WaitStage.RECOGNITION,1200);s.recordWait(now,UsageStats.WaitStage.EDITING,800);
        s.recordSpeech(now-10L*86400000,"old words",60000);var week=s.performance(7);assertEquals(120,week.wordsPerMinute());assertEquals(1200,week.recognitionMs());assertEquals(1,week.recognitionCount());assertEquals(800,week.editingMs());assertEquals(6,s.performance(30).speechWords());s.clear();assertEquals(0,s.performance(365).speechMs());
    }
    @Test public void selectedSessionExportContainsOnlyItsEventsAndNoPrivatePayload()throws Exception{
        var context=RuntimeEnvironment.getApplication();DiagnosticLog log=new DiagnosticLog(context);String a=DiagnosticLog.session(),b=DiagnosticLog.session();log.event(a,DiagnosticLog.Stage.TRANSCRIPTION,DiagnosticLog.Outcome.START,"clean",10,-1,-1);log.event(b,DiagnosticLog.Stage.CLEANUP,DiagnosticLog.Outcome.FAILURE,"clean",10,-1,-1);
        var report=new org.json.JSONObject(log.report(a));assertEquals(1,report.getJSONArray("events").length());assertEquals(a,report.getJSONArray("events").getJSONObject(0).getString("session"));String exported=new String(Files.readAllBytes(log.export(a).toPath()),java.nio.charset.StandardCharsets.UTF_8);assertFalse(exported.contains(b));assertThrows(IllegalArgumentException.class,()->log.report("not-a-session"));assertEquals(2,new org.json.JSONObject(log.report()).getJSONArray("events").length());
    }
    @Test public void tileOnlyChangesVisibilityAndDoesNotRecordOrChangeTheKeyboard(){
        final int[] changes={0};CompanionControl.Listener listener=()->changes[0]++;CompanionControl.attach(listener);var controller=Robolectric.buildService(WispTileService.class).create();try{var tile=controller.get();Shadows.shadowOf(tile).setLocked(false);SecureSettings settings=new SecureSettings(tile);assertTrue(settings.companionVisible());tile.onClick();assertFalse(settings.companionVisible());assertEquals(1,changes[0]);tile.onClick();assertTrue(settings.companionVisible());assertEquals(2,changes[0]);assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());}finally{CompanionControl.detach(listener);/* Robolectric 4.14.1 ShadowTileService is not a ShadowService: platform onDestroy casts incorrectly. No owned resources here; sandbox teardown resets it. */}
    }
    @Test public void disabledAnimationsLeaveTouchTargetsUnscaled(){
        var context=RuntimeEnvironment.getApplication();android.provider.Settings.Global.putFloat(context.getContentResolver(),android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE,0);View v=new View(context);UiMotion.press(v,true);assertEquals(1,v.getScaleX(),0);UiMotion.enter(v,true);assertEquals(1,v.getAlpha(),0);UiMotion.reset(v);assertFalse(v.isPressed());
    }
}
