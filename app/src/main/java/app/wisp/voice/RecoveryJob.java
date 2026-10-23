package app.wisp.voice;

import java.io.File;
import java.util.function.Consumer;

/** Checkpoints each paid stage. A retry never reuploads audio if transcription already exists. */
final class RecoveryJob {
    static DictationPipeline.Result run(RecoveryStore store,RecoveryStore.Entry entry,File cache,GroqClient api,String key,Consumer<String> recognized)throws Exception{
        if(!entry.text.isEmpty()&&entry.error.isEmpty())return new DictationPipeline.Result(entry.original,entry.text,"");
        if(entry.original.isEmpty()){
            File audio=store.audio(entry,cache);
            try{entry.original=api.transcribe(audio,key,entry.language,entry.turbo,entry.vocabulary);store.save(entry);}
            finally{audio.delete();}
        }
        recognized.accept(entry.original);
        var result=DictationPipeline.finish(api,entry.original,key,entry.vocabulary,entry.mode,entry.emojis);
        entry.text=result.text();entry.error=result.errorCategory();store.save(entry);return result;
    }
    private RecoveryJob(){}
}
