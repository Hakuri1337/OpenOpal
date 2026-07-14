package wtf.opal.client.music.model;

import java.net.URI;

public record AudioSource(
        long songId,
        URI uri,
        MusicQuality requestedQuality,
        MusicQuality actualQuality,
        long size,
        String md5
) {
    public AudioSource {
        md5 = md5 == null ? "" : md5.toLowerCase();
        size = Math.max(0, size);
    }
}
