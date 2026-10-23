package app.wisp.voice;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class UsageStatsTest {
    private UsageStats stats;
    @Before public void setup(){stats=new UsageStats(RuntimeEnvironment.getApplication());stats.clear();}
    @Test public void countersPersistAcrossStoreInstancesAndAreGroupedByRecordingDay(){
        long when=System.currentTimeMillis();stats.recordUse(when,65000);stats.recordInsertion(when,"Ну, API готов.","API готов.","clean",true);
        var s=new UsageStats(RuntimeEnvironment.getApplication()).snapshot();
        assertEquals(1,s.sessions());assertEquals(65000,s.durationMs());assertEquals(2,s.words());
        assertEquals(new UsageStats.Day(1,2,65000),s.days().get(UsageStats.day(when)));assertEquals(Long.valueOf(1),s.fillers().get("ну"));
    }
    @Test public void countsOnlyRemovedFillersWithWholeWordBoundaries(){
        stats.recordInsertion(System.currentTimeMillis(),"Ну, ну, типа, э-э, как бы прототип готов.","Ну, прототип готов.","clean",true);
        var values=stats.snapshot().fillers();assertEquals(Long.valueOf(1),values.get("ну"));assertEquals(Long.valueOf(1),values.get("типа"));
        assertEquals(Long.valueOf(1),values.get("э-э"));assertEquals(Long.valueOf(1),values.get("как бы"));assertEquals(4,values.size());
    }
    @Test public void translationRawAndFailedCleanupDoNotClaimToRemoveFillers(){
        long now=System.currentTimeMillis();stats.recordInsertion(now,"Ну, готово.","Ready.","translate",true);
        stats.recordInsertion(now,"Ну, готово.","Ну, готово.","raw",true);stats.recordInsertion(now,"Ну, готово.","Готово.","clean",false);
        assertTrue(stats.snapshot().fillers().isEmpty());assertEquals(4,stats.snapshot().words());
    }
    @Test public void aggregateStorageContainsNoDictatedTextOrArbitraryErrorMessages(){
        String privateText="Секретный адрес частного клиента 12345.";long now=System.currentTimeMillis();
        stats.recordInsertion(now,privateText,privateText,"clean",true);stats.recordFailure("private provider message and credential");
        String persisted=RuntimeEnvironment.getApplication().getSharedPreferences("wisp_usage",0).getString("counters","");
        assertFalse(persisted.contains("Секретный"));assertFalse(persisted.contains("12345"));assertFalse(persisted.contains("credential"));
        assertEquals(Map.of("other",1L),stats.snapshot().errors());
    }
    @Test public void keepsFullYearOfDailyBucketsAndAllTimeTotals(){
        LocalDate today=LocalDate.now();for(int i=0;i<400;i++)stats.recordUse(today.minusDays(i).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),1000);
        var s=stats.snapshot();assertEquals(400,s.sessions());assertEquals(400000,s.durationMs());assertEquals(365,s.days().size());
        assertTrue(s.days().containsKey(today.minusDays(364).toString()));assertFalse(s.days().containsKey(today.minusDays(365).toString()));
    }
    @Test public void dayBoundaryUsesPhoneTimezone(){
        TimeZone original=TimeZone.getDefault();try{
            TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"));LocalDate today=LocalDate.now();
            long first=today.atTime(23,59,59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();long second=first+2000;
            stats.recordUse(first,1000);stats.recordUse(second,1000);
            assertEquals(1,stats.snapshot().days().get(today.toString()).sessions());assertEquals(1,stats.snapshot().days().get(today.plusDays(1).toString()).sessions());
        }finally{TimeZone.setDefault(original);}
    }
    @Test public void simultaneousWritersDoNotLoseSessions()throws Exception{
        ExecutorService workers=Executors.newFixedThreadPool(4);try{
            List<Future<?>> jobs=new ArrayList<>();long now=System.currentTimeMillis();
            for(int i=0;i<80;i++)jobs.add(workers.submit(()->new UsageStats(RuntimeEnvironment.getApplication()).recordUse(now,1000)));
            for(Future<?> job:jobs)job.get(5,TimeUnit.SECONDS);
            assertEquals(80,stats.snapshot().sessions());assertEquals(80000,stats.snapshot().durationMs());
        }finally{workers.shutdownNow();}
    }
    @Test public void clearRemovesTotalsDailyDataFillersAndErrors(){
        long now=System.currentTimeMillis();stats.recordUse(now,1000);stats.recordInsertion(now,"Ну, готово.","Готово.","clean",true);stats.recordFailure("limit");stats.clear();
        var s=stats.snapshot();assertEquals(0,s.sessions());assertEquals(0,s.words());assertEquals(0,s.durationMs());assertTrue(s.days().isEmpty());assertTrue(s.fillers().isEmpty());assertTrue(s.errors().isEmpty());
    }
    @Test public void wordCounterHandlesMixedLanguageNumbersAndHyphenatedWords(){
        assertEquals(7,UsageStats.words("Проверь API и pull request, it's ready."));assertEquals(2,UsageStats.words("self-hosted 123"));assertEquals(0,UsageStats.words("… — !"));
    }
}
