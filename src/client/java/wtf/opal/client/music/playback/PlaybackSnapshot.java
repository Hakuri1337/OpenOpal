package wtf.opal.client.music.playback;

import wtf.opal.client.music.model.Song;

public record PlaybackSnapshot(
        PlaybackState state,
        Song song,
        long positionMillis,
        long durationMillis,
        float volume,
        int queueIndex,
        int queueSize,
        RepeatMode repeatMode,
        boolean shuffle,
        String error
) {
    public static PlaybackSnapshot idle() {
        return new PlaybackSnapshot(PlaybackState.IDLE, null, 0, 0, 0.7F, -1, 0, RepeatMode.OFF, false, "");
    }
}
