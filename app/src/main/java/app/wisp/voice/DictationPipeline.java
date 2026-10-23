package app.wisp.voice;

import java.io.File;
import java.io.InterruptedIOException;
import java.util.function.Consumer;

final class DictationPipeline {
    record Result(String original,String text,String warning,String errorCategory) {
        Result(String original,String text,String warning){this(original,text,warning,"");}
    }
    static Result run(GroqClient api,File audio,String key,String language,boolean turbo,String vocabulary,
                      String mode,Consumer<String> recognized) throws Exception {
        return run(api,audio,key,language,turbo,vocabulary,mode,false,recognized);
    }
    static Result run(GroqClient api,File audio,String key,String language,boolean turbo,String vocabulary,
                      String mode,boolean emojis,Consumer<String> recognized) throws Exception {
        String original=api.transcribe(audio,key,language,turbo,vocabulary);
        if("raw".equals(mode))return new Result(original,original,"");
        recognized.accept(original);
        return finish(api,original,key,vocabulary,mode,emojis);
    }
    static Result finish(GroqClient api,String original,String key,String vocabulary,String mode,boolean emojis)throws Exception {
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
        if("raw".equals(mode))return new Result(original,original,"");
        try {
            String text="translate".equals(mode)?api.translate(original,key,vocabulary,emojis):api.clean(original,key,vocabulary,emojis);
            api.processingResult(original,text);
            return new Result(original,text,"");
        } catch(Exception e) {
            if(Thread.currentThread().isInterrupted()||e instanceof InterruptedIOException && !(e instanceof java.net.SocketTimeoutException))throw e;
            api.processingFallback(e);
            String task="translate".equals(mode)?"Перевод не удался.":"Очистка не удалась.";
            return new Result(original,original,task+" Сохранён исходный текст. "+GroqClient.friendlyError(e),GroqClient.failureCategory(e));
        }
    }
}
