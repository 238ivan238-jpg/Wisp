package app.wisp.voice;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/** Local aggregate counters only: no audio, transcripts, keys or arbitrary provider messages. */
final class UsageStats {
    static final int RETENTION_DAYS=365;
    private static final Object LOCK=new Object();
    private static final Pattern WORD=Pattern.compile("[\\p{L}\\p{N}]+(?:['’_-][\\p{L}\\p{N}]+)*");
    static final Map<String,String> ERRORS=Map.ofEntries(
        Map.entry("auth","API-ключ"),Map.entry("access","Доступ к Groq"),Map.entry("billing","Оплата Groq"),
        Map.entry("limit","Лимит Groq"),Map.entry("size","Размер записи"),Map.entry("request","Параметры запроса"),
        Map.entry("model","Недоступная модель"),Map.entry("server","Сервер Groq"),Map.entry("response","Ответ обработки"),
        Map.entry("timeout","Время ожидания"),Map.entry("tls","Защищённое соединение"),Map.entry("network","Сеть"),
        Map.entry("audio","Подготовка аудио"),Map.entry("input","Вставка текста"),Map.entry("microphone","Запись микрофона"),Map.entry("other","Другое"));
    private static final Map<String,Pattern> FILLERS;
    static {
        Map<String,Pattern> patterns=new LinkedHashMap<>();
        for(String phrase:List.of("ну","эм","э-э","м-м","как бы","типа","короче","в общем","в принципе","получается","так сказать","um","uh","erm","you know","I mean","sort of")){
            String joined=Arrays.stream(phrase.split(" ")).map(Pattern::quote).collect(java.util.stream.Collectors.joining("\\s+"));
            patterns.put(phrase,Pattern.compile("(?<![\\p{L}\\p{N}_])"+joined+"(?![\\p{L}\\p{N}_])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE));
        }
        FILLERS=Collections.unmodifiableMap(patterns);
    }
    record Day(long sessions,long words,long durationMs) {}
    record Snapshot(long sessions,long words,long durationMs,Map<String,Day> days,Map<String,Long> fillers,Map<String,Long> errors) {}
    private final SharedPreferences prefs;
    UsageStats(Context context){prefs=context.getSharedPreferences("wisp_usage",Context.MODE_PRIVATE);}
    private interface Mutation {void apply(JSONObject value)throws Exception;}
    private JSONObject read(){try{return new JSONObject(prefs.getString("counters","{}"));}catch(Exception ignored){return new JSONObject();}}
    private static JSONObject child(JSONObject parent,String key)throws Exception{
        JSONObject value=parent.optJSONObject(key);if(value==null){value=new JSONObject();parent.put(key,value);}return value;
    }
    private static void add(JSONObject value,String key,long count)throws Exception{
        if(count>0)value.put(key,Math.max(0,value.optLong(key))+count);
    }
    static String day(long when){return Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault()).toLocalDate().toString();}
    private static JSONObject daily(JSONObject value,long when)throws Exception{return child(child(value,"days"),day(when));}
    private void update(Mutation mutation){
        synchronized(LOCK){try{
            JSONObject value=read();mutation.apply(value);
            JSONObject days=value.optJSONObject("days");
            if(days!=null){
                String cutoff=LocalDate.now().minusDays(RETENTION_DAYS-1).toString();List<String> keys=new ArrayList<>();days.keys().forEachRemaining(keys::add);Collections.sort(keys);
                for(String key:keys)if(key.compareTo(cutoff)<0)days.remove(key);
                keys.clear();days.keys().forEachRemaining(keys::add);Collections.sort(keys);
                for(int i=0;i<keys.size()-RETENTION_DAYS;i++)days.remove(keys.get(i));
            }
            prefs.edit().putString("counters",value.toString()).apply();
        }catch(Exception ignored){/* Statistics must not interrupt dictation. */}}
    }
    void recordUse(long when,long durationMs){
        long duration=Math.max(0,Math.min(RecordingLimits.MAX_DURATION_MS,durationMs));
        update(v->{add(v,"sessions",1);add(v,"durationMs",duration);JSONObject d=daily(v,when);add(d,"sessions",1);add(d,"durationMs",duration);});
    }
    static long words(String text){long count=0;var matcher=WORD.matcher(text==null?"":text);while(matcher.find())count++;return count;}
    private static long occurrences(Pattern pattern,String text){long count=0;var matcher=pattern.matcher(text);while(matcher.find())count++;return count;}
    void recordInsertion(long when,String original,String inserted,String mode,boolean processed){
        long count=words(inserted);
        update(v->{
            add(v,"words",count);add(daily(v,when),"words",count);
            if(processed&&"clean".equals(mode)){
                JSONObject totals=child(v,"fillers");
                for(var entry:FILLERS.entrySet()){
                    long removed=occurrences(entry.getValue(),original)-occurrences(entry.getValue(),inserted);
                    add(totals,entry.getKey(),removed);
                }
            }
        });
    }
    enum WaitStage {RECOGNITION,EDITING}
    record Performance(long speechWords,long speechMs,long recognitionMs,long recognitionCount,long editingMs,long editingCount){
        long wordsPerMinute(){return speechMs>0?Math.round(speechWords*60000d/speechMs):0;}
    }
    void recordSpeech(long when,String original,long durationMs){
        long count=words(original),duration=Math.max(0,durationMs);
        if(count==0||duration==0)return;
        update(v->{JSONObject d=daily(v,when);add(d,"speechWords",count);add(d,"speechMs",duration);});
    }
    void recordWait(long when,WaitStage stage,long ms){
        String prefix=stage==WaitStage.RECOGNITION?"recognition":"editing";
        update(v->{JSONObject d=daily(v,when);add(d,prefix+"Ms",Math.max(0,ms));add(d,prefix+"Count",1);});
    }
    Performance performance(int period){
        synchronized(LOCK){JSONObject days=read().optJSONObject("days");long[] sum=new long[6];
            if(days!=null){String earliest=LocalDate.now().minusDays(Math.max(1,Math.min(365,period))-1).toString(),today=LocalDate.now().toString();
                var keys=days.keys();while(keys.hasNext()){String date=keys.next();if(date.compareTo(earliest)<0||date.compareTo(today)>0)continue;JSONObject d=days.optJSONObject(date);if(d==null)continue;
                    String[] fields={"speechWords","speechMs","recognitionMs","recognitionCount","editingMs","editingCount"};for(int i=0;i<fields.length;i++)sum[i]+=Math.max(0,d.optLong(fields[i]));}}
            return new Performance(sum[0],sum[1],sum[2],sum[3],sum[4],sum[5]);}
    }
    void recordFailure(String category){String key=ERRORS.containsKey(category)?category:"other";update(v->add(child(v,"errors"),key,1));}
    private static Map<String,Long> counters(JSONObject value,Set<String> allowed){
        Map<String,Long> out=new HashMap<>();if(value!=null)for(String key:allowed){long n=value.optLong(key);if(n>0)out.put(key,n);}return Map.copyOf(out);
    }
    Snapshot snapshot(){
        synchronized(LOCK){JSONObject value=read();Map<String,Day> days=new TreeMap<>();JSONObject raw=value.optJSONObject("days");
            if(raw!=null){Iterator<String> keys=raw.keys();while(keys.hasNext()){
                String key=keys.next();JSONObject d=raw.optJSONObject(key);
                if(d!=null&&key.compareTo(LocalDate.now().minusDays(RETENTION_DAYS-1).toString())>=0)
                    days.put(key,new Day(d.optLong("sessions"),d.optLong("words"),d.optLong("durationMs")));
            }}
            return new Snapshot(value.optLong("sessions"),value.optLong("words"),value.optLong("durationMs"),Map.copyOf(days),
                counters(value.optJSONObject("fillers"),FILLERS.keySet()),counters(value.optJSONObject("errors"),ERRORS.keySet()));
        }
    }
    void clear(){synchronized(LOCK){prefs.edit().clear().apply();}}
}
