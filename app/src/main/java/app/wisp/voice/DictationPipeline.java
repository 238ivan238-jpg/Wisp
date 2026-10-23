package app.wisp.voice;

import java.io.File;
import java.io.InterruptedIOException;
import java.util.function.Consumer;

final class DictationPipeline {
    record Result(String original,String text,String warning) {}
    static Result run(GroqClient api,File audio,String key,String language,boolean turbo,String vocabulary,
                      String mode,Consumer<String> recognized) throws Exception {
        String original=api.transcribe(audio,key,language,turbo,vocabulary);
        if("raw".equals(mode))return new Result(original,original,"");
        recognized.accept(original);
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
        try {
            String text=api.clean(original,key,mode,vocabulary);
            return new Result(original,text,"");
        } catch(Exception e) {
            if(Thread.currentThread().isInterrupted()||e instanceof InterruptedIOException && !(e instanceof java.net.SocketTimeoutException))throw e;
            return new Result(original,original,"Очистка не удалась. Сохранён исходный текст. "+GroqClient.friendlyError(e));
        }
    }
}
