package wtf.opal.client.music.model;

public record LyricDocument(String original, String translated, String romanized, String wordTimed) {
    public LyricDocument {
        original = safe(original);
        translated = safe(translated);
        romanized = safe(romanized);
        wordTimed = safe(wordTimed);
    }

    private static String safe(final String value) {
        return value == null ? "" : value;
    }
}
