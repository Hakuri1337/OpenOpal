package wtf.opal.client.music.api;

public final class MusicApiException extends RuntimeException {
    public MusicApiException(final String message) {
        super(message);
    }

    public MusicApiException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
