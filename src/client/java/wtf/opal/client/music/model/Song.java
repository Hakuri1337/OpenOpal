package wtf.opal.client.music.model;

public record Song(
        long id,
        String name,
        String singer,
        String album,
        String artworkUrl,
        long durationMillis,
        boolean free,
        int copyright
) {
    public Song {
        name = safe(name, "Unknown song");
        singer = safe(singer, "Unknown artist");
        album = safe(album, "Unknown album");
        artworkUrl = safe(artworkUrl, "");
        durationMillis = Math.max(0, durationMillis);
    }

    private static String safe(final String value, final String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
