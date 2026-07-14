package wtf.opal.client.music.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import wtf.opal.client.music.model.Song;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MusicLibraryRepository {
    private static final int MAX_HISTORY = 500;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private LibraryState state;

    public MusicLibraryRepository(final Path directory) {
        this.file = directory.resolve("library.json");
        this.state = load();
    }

    public synchronized List<Song> getFavorites() {
        return List.copyOf(state.favorites.values());
    }

    public synchronized boolean isFavorite(final long songId) {
        return state.favorites.containsKey(Long.toString(songId));
    }

    public synchronized void toggleFavorite(final Song song) {
        final String key = Long.toString(song.id());
        if (state.favorites.remove(key) == null) {
            state.favorites.put(key, song);
        }
        save();
    }

    public synchronized List<HistoryEntry> getHistory() {
        return List.copyOf(state.history);
    }

    public synchronized void recordPlay(final Song song) {
        int plays = 1;
        for (final HistoryEntry entry : state.history) {
            if (entry.song().id() == song.id()) {
                plays = entry.playCount() + 1;
                break;
            }
        }
        final int finalPlays = plays;
        state.history.removeIf(entry -> entry.song().id() == song.id());
        state.history.addFirst(new HistoryEntry(song, System.currentTimeMillis(), finalPlays));
        while (state.history.size() > MAX_HISTORY) state.history.removeLast();
        save();
    }

    public synchronized Map<String, List<Song>> getLocalPlaylists() {
        final Map<String, List<Song>> copy = new LinkedHashMap<>();
        state.localPlaylists.forEach((name, songs) -> copy.put(name, List.copyOf(songs)));
        return copy;
    }

    public synchronized void putLocalPlaylist(final String name, final List<Song> songs) {
        if (name == null || name.isBlank()) return;
        state.localPlaylists.put(name.trim(), new ArrayList<>(songs));
        save();
    }

    public synchronized List<Long> getPinnedPlaylistIds() {
        return List.copyOf(state.pinnedPlaylistIds);
    }

    public synchronized void togglePinnedPlaylist(final long playlistId) {
        if (!state.pinnedPlaylistIds.remove(playlistId)) state.pinnedPlaylistIds.add(playlistId);
        save();
    }

    public synchronized void pinPlaylist(final long playlistId) {
        if (!state.pinnedPlaylistIds.contains(playlistId)) {
            state.pinnedPlaylistIds.add(playlistId);
            save();
        }
    }

    private LibraryState load() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) return new LibraryState();
            final LibraryState loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LibraryState.class);
            return loaded == null ? new LibraryState() : loaded.normalize();
        } catch (final RuntimeException | IOException exception) {
            return new LibraryState();
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            final Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(state), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (final AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (final IOException exception) {
            throw new IllegalStateException("Unable to save music library", exception);
        }
    }

    public record HistoryEntry(Song song, long playedAt, int playCount) {
    }

    private static final class LibraryState {
        private int schemaVersion = 1;
        private Map<String, Song> favorites = new LinkedHashMap<>();
        private List<HistoryEntry> history = new ArrayList<>();
        private Map<String, List<Song>> localPlaylists = new LinkedHashMap<>();
        private List<Long> pinnedPlaylistIds = new ArrayList<>();

        private LibraryState normalize() {
            if (favorites == null) favorites = new LinkedHashMap<>();
            if (history == null) history = new ArrayList<>();
            if (localPlaylists == null) localPlaylists = new LinkedHashMap<>();
            if (pinnedPlaylistIds == null) pinnedPlaylistIds = new ArrayList<>();
            return this;
        }
    }
}
