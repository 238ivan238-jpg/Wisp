package app.wisp.voice;

import java.io.*;
import java.util.function.Consumer;

/** Durable stage/part checkpoints. A manual retry resumes, never re-inserts on its own. */
final class RecoveryJob {
    interface Observer {
        default void progress(JobProgress progress){}
        default void recognized(String original){}
        default void speech(RecoveryStore.Entry entry){}
        default void timing(RecoveryStore.Entry entry,UsageStats.WaitStage stage,long ms){}
    }
    static DictationPipeline.Result run(RecoveryStore store,RecoveryStore.Entry entry,File cache,GroqClient api,String key,Consumer<String> recognized)throws Exception{
        return run(store,entry,cache,api,key,new Observer(){public void recognized(String value){recognized.accept(value);}});
    }
    static DictationPipeline.Result run(RecoveryStore store,RecoveryStore.Entry entry,File cache,GroqClient api,String key,Observer observer)throws Exception{
        return run(store,entry,cache,api,key,observer,AudioChunks::open);
    }
    static DictationPipeline.Result run(RecoveryStore store,RecoveryStore.Entry entry,File cache,GroqClient api,String key,Observer observer,AudioChunks.Factory factory)throws Exception{
        if(!entry.text.isEmpty()&&entry.error.isEmpty())return new DictationPipeline.Result(entry.original,entry.text,"");
        if(entry.original.isEmpty()){
            observer.progress(new JobProgress(JobProgress.Stage.PREPARING));File audio=store.audio(entry,cache);
            try(AudioChunks chunks=factory.open(audio,cache,entry.duration)){
                if(entry.parts.size()>chunks.files.size())throw new IOException("Part plan changed");
                entry.partCount=chunks.files.size();store.save(entry);
                for(int i=0;i<chunks.files.size();i++){
                    interrupted();File part=chunks.files.get(i);String hash=AudioChunks.fingerprint(part);
                    if(i<entry.parts.size()){if(!hash.equals(entry.parts.get(i).hash()))throw new IOException("Part plan changed");continue;}
                    observer.progress(new JobProgress(JobProgress.Stage.RECOGNIZING,i+1,chunks.files.size()));long started=System.nanoTime();
                    String text=api.transcribe(part,key,entry.language,entry.turbo,entry.vocabulary);
                    entry.recognitionMs+=Math.max(0,(System.nanoTime()-started)/1_000_000);entry.parts.add(new RecoveryStore.Entry.Part(hash,text));store.save(entry);
                }
                entry.original=entry.availableText();store.save(entry);
            }finally{audio.delete();}
        }
        if(!entry.speechCounted){entry.speechCounted=true;store.save(entry);observer.speech(entry);}
        if(!entry.recognitionCounted&&entry.recognitionMs>0){entry.recognitionCounted=true;store.save(entry);observer.timing(entry,UsageStats.WaitStage.RECOGNITION,entry.recognitionMs);}
        observer.recognized(entry.original);interrupted();
        if("raw".equals(entry.mode)){entry.text=entry.original;entry.error="";store.save(entry);return new DictationPipeline.Result(entry.original,entry.original,"");}
        observer.progress(new JobProgress("translate".equals(entry.mode)?JobProgress.Stage.TRANSLATING:JobProgress.Stage.EDITING));
        long started=System.nanoTime();var result=DictationPipeline.finish(api,entry.original,key,entry.vocabulary,entry.mode,entry.emojis,entry.style);
        long elapsed=Math.max(0,(System.nanoTime()-started)/1_000_000);entry.text=result.text();entry.error=result.errorCategory();store.save(entry);
        if(!"raw".equals(entry.mode)&&result.errorCategory().isEmpty()&&!entry.editingCounted){entry.editingCounted=true;store.save(entry);observer.timing(entry,UsageStats.WaitStage.EDITING,elapsed);}
        return result;
    }
    private static void interrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
    private RecoveryJob(){}
}
