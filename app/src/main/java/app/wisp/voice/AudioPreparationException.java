package app.wisp.voice;

final class AudioPreparationException extends java.io.IOException {
    AudioPreparationException(Throwable cause){super("Audio preparation failed",cause);}
}
