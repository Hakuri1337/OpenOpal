package wtf.opal.client.music;

import com.google.gson.JsonObject;
import wtf.opal.client.feature.module.impl.visual.overlay.impl.dynamicisland.DynamicIslandElement;
import wtf.opal.client.music.api.MusicApiClient;
import wtf.opal.client.music.cache.MusicCache;
import wtf.opal.client.music.cache.MusicArtworkCache;
import wtf.opal.client.music.island.MusicIslandTrigger;
import wtf.opal.client.music.library.MusicLibraryRepository;
import wtf.opal.client.music.library.MusicSettingsRepository;
import wtf.opal.client.music.model.CommentPage;
import wtf.opal.client.music.model.LyricDocument;
import wtf.opal.client.music.model.LyricTimeline;
import wtf.opal.client.music.model.MusicQuality;
import wtf.opal.client.music.model.RemotePlaylist;
import wtf.opal.client.music.model.Song;
import wtf.opal.client.music.model.ToplistEntry;
import wtf.opal.client.music.playback.OpenAlMusicPlayer;
import wtf.opal.client.music.playback.PlaybackSnapshot;
import wtf.opal.client.music.playback.PlaybackState;
import wtf.opal.client.music.playback.RepeatMode;
import wtf.opal.event.EventDispatcher;
import wtf.opal.event.impl.game.server.ServerDisconnectEvent;
import wtf.opal.event.subscriber.IEventSubscriber;
import wtf.opal.event.subscriber.Subscribe;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static wtf.opal.client.Constants.DIRECTORY;

public final class MusicService implements AutoCloseable, IEventSubscriber {
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(3,
            Thread.ofPlatform().daemon().name("OpenOpal Music IO-", 0).factory());
    private final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("OpenOpal Music Monitor").factory());

    private final MusicApiClient apiClient = new MusicApiClient();
    private final MusicLibraryRepository library;
    private final MusicSettingsRepository settingsRepository;
    private final MusicCache cache;
    private final MusicArtworkCache artworkCache;
    private final OpenAlMusicPlayer player = new OpenAlMusicPlayer();
    private final MusicIslandTrigger islandTrigger;
    private final AtomicLong operationGeneration = new AtomicLong();

    private final List<Song> queue = new ArrayList<>();
    private volatile PlaybackSnapshot snapshot = PlaybackSnapshot.idle();
    private volatile List<Song> searchResults = List.of();
    private volatile String searchError = "";
    private volatile String playlistStatus = "";
    private volatile Path currentArtworkPath;
    private volatile LyricTimeline lyricTimeline = LyricTimeline.empty();

    private Song currentSong;
    private Path currentAudioPath;
    private int queueIndex = -1;
    private PlaybackState state = PlaybackState.IDLE;
    private RepeatMode repeatMode;
    private boolean shuffle;
    private float volume;
    private String error = "";
    private boolean pauseRequested;
    private long pausedPositionMillis;
    private boolean closed;

    public MusicService() {
        final Path musicDirectory = DIRECTORY.toPath().resolve("music");
        this.library = new MusicLibraryRepository(musicDirectory);
        this.settingsRepository = new MusicSettingsRepository(musicDirectory);
        final MusicSettingsRepository.Settings settings = settingsRepository.get();
        this.volume = settings.volume();
        this.repeatMode = settings.repeatMode();
        this.shuffle = settings.shuffle();
        this.cache = new MusicCache(musicDirectory.resolve("cache"), apiClient.getHttpClient(), ioExecutor);
        this.artworkCache = new MusicArtworkCache(musicDirectory.resolve("cache").resolve("artwork"), apiClient.getHttpClient(), ioExecutor);
        this.cache.setMaximumBytes(settings.cacheLimitBytes());
        this.islandTrigger = new MusicIslandTrigger(this);
        EventDispatcher.subscribe(this);
        updateSnapshot();
        monitor.scheduleAtFixedRate(this::monitorPlayback, 100, 100, TimeUnit.MILLISECONDS);
    }

    public PlaybackSnapshot getSnapshot() {
        return snapshot;
    }

    public List<Song> getQueue() {
        synchronized (this) {
            return List.copyOf(queue);
        }
    }

    public List<Song> getSearchResults() {
        return searchResults;
    }

    public String getSearchError() {
        return searchError;
    }

    public MusicLibraryRepository getLibrary() {
        return library;
    }

    public Path getCurrentArtworkPath() {
        return currentArtworkPath;
    }

    public LyricTimeline.Line getCurrentLyricLine(final long positionMillis) {
        return lyricTimeline.lineAt(positionMillis);
    }

    public String getPlaylistStatus() {
        return playlistStatus;
    }

    public List<Song> getImportedPlaylistSongs() {
        final Map<Long, Song> songs = new LinkedHashMap<>();
        library.getLocalPlaylists().values().forEach(list -> list.forEach(song -> songs.putIfAbsent(song.id(), song)));
        return List.copyOf(songs.values());
    }

    public CompletableFuture<List<Song>> search(final String query) {
        if (query == null || query.isBlank()) {
            searchResults = List.of();
            searchError = "";
            return CompletableFuture.completedFuture(searchResults);
        }
        searchError = "";
        return apiClient.search(query.trim(), 100, 0).whenComplete((songs, throwable) -> {
            if (throwable == null) {
                searchResults = songs;
            } else {
                searchError = readableError(throwable);
            }
        });
    }

    public CompletableFuture<Song> playById(final long songId) {
        return apiClient.getSongInfo(songId).thenApply(song -> {
            playNow(song);
            return song;
        });
    }

    public synchronized void playNow(final Song song) {
        pauseRequested = false;
        queue.clear();
        queue.add(song);
        queueIndex = 0;
        startCurrent(0);
    }

    public synchronized void playQueue(final List<Song> songs, final int index) {
        if (songs == null || songs.isEmpty()) return;
        pauseRequested = false;
        queue.clear();
        queue.addAll(songs);
        queueIndex = Math.clamp(index, 0, queue.size() - 1);
        startCurrent(0);
    }

    public synchronized void addToQueue(final Song song) {
        queue.add(song);
        if (currentSong == null) {
            queueIndex = 0;
            startCurrent(0);
        } else {
            updateSnapshot();
        }
    }

    public synchronized void togglePause() {
        if (currentSong == null || state == PlaybackState.IDLE || state == PlaybackState.ERROR) {
            return;
        }

        if (pauseRequested || state == PlaybackState.PAUSED) {
            resumePlayback();
        } else {
            pausePlayback();
        }
    }

    @Subscribe
    public void onServerDisconnect(final ServerDisconnectEvent event) {
        pausePlayback();
    }

    public synchronized void next() {
        if (queue.isEmpty()) return;
        if (repeatMode == RepeatMode.ONE && currentSong != null) {
            startCurrent(0);
            return;
        }
        if (shuffle && queue.size() > 1) {
            int next;
            do {
                next = java.util.concurrent.ThreadLocalRandom.current().nextInt(queue.size());
            } while (next == queueIndex);
            queueIndex = next;
        } else if (queueIndex + 1 < queue.size()) {
            queueIndex++;
        } else if (repeatMode == RepeatMode.ALL) {
            queueIndex = 0;
        } else {
            stop();
            return;
        }
        startCurrent(0);
    }

    public synchronized void previous() {
        if (currentSong == null) return;
        if (player.getPositionMillis() > 5000 || queueIndex <= 0) {
            seek(0);
            return;
        }
        queueIndex--;
        startCurrent(0);
    }

    public synchronized void seek(final long requestedMillis) {
        if (currentSong == null || currentAudioPath == null) return;
        final long target = Math.clamp(requestedMillis, 0, Math.max(0, currentSong.durationMillis() - 250));
        final boolean remainPaused = pauseRequested || state == PlaybackState.PAUSED;
        pauseRequested = remainPaused;
        final long generation = operationGeneration.incrementAndGet();
        if (remainPaused) {
            pausedPositionMillis = target;
            player.stop();
            state = PlaybackState.PAUSED;
            DynamicIslandElement.removeTrigger(islandTrigger);
            updateSnapshot(target);
            return;
        }
        state = remainPaused ? PlaybackState.PAUSED : PlaybackState.SEEKING;
        registerIsland();
        updateSnapshot(target);
        player.play(currentAudioPath, target, volume).whenComplete((ignored, throwable) -> {
            synchronized (this) {
                if (operationGeneration.get() != generation || closed) return;
                if (throwable != null) {
                    fail(throwable);
                    return;
                }
                if (pauseRequested) {
                    pausePlayback();
                    return;
                }
                pausedPositionMillis = 0L;
                state = PlaybackState.PLAYING;
                updateSnapshot();
            }
        });
    }

    public synchronized void setVolume(final float value) {
        volume = Math.clamp(value, 0.0F, 1.0F);
        player.setVolume(volume);
        persistSettings();
        updateSnapshot();
    }

    public synchronized void cycleRepeatMode() {
        repeatMode = switch (repeatMode) {
            case OFF -> RepeatMode.ALL;
            case ALL -> RepeatMode.ONE;
            case ONE -> RepeatMode.OFF;
        };
        persistSettings();
        updateSnapshot();
    }

    public synchronized void toggleShuffle() {
        shuffle = !shuffle;
        persistSettings();
        updateSnapshot();
    }

    public synchronized void stop() {
        operationGeneration.incrementAndGet();
        pauseRequested = false;
        pausedPositionMillis = 0L;
        player.stop();
        currentSong = null;
        currentAudioPath = null;
        currentArtworkPath = null;
        lyricTimeline = LyricTimeline.empty();
        state = PlaybackState.IDLE;
        error = "";
        queueIndex = queue.isEmpty() ? -1 : queueIndex;
        DynamicIslandElement.removeTrigger(islandTrigger);
        updateSnapshot();
    }

    public void toggleFavorite(final Song song) {
        library.toggleFavorite(song);
    }

    public CompletableFuture<LyricDocument> getLyrics(final long songId) {
        return apiClient.getLyrics(songId);
    }

    public CompletableFuture<List<ToplistEntry>> getToplists() {
        return apiClient.getToplists();
    }

    public CompletableFuture<RemotePlaylist> getPlaylist(final long playlistId) {
        return apiClient.getPlaylist(playlistId);
    }

    public CompletableFuture<RemotePlaylist> importPlaylist(final String input) {
        final long playlistId;
        try {
            playlistId = parsePlaylistId(input);
        } catch (final IllegalArgumentException exception) {
            playlistStatus = exception.getMessage();
            return CompletableFuture.failedFuture(exception);
        }

        playlistStatus = "Importing playlist " + playlistId + "...";
        return getPlaylist(playlistId).whenComplete((playlist, throwable) -> {
            if (throwable != null) {
                playlistStatus = readableError(throwable);
                return;
            }
            if (playlist.songs().isEmpty()) {
                playlistStatus = "The playlist contains no available songs";
                return;
            }
            library.putLocalPlaylist(playlist.name(), playlist.songs());
            library.pinPlaylist(playlist.id());
            playlistStatus = "Imported " + playlist.name() + " (" + playlist.songs().size() + ")";
        });
    }

    public CompletableFuture<RemotePlaylist> getAlbum(final long albumId) {
        return apiClient.getAlbum(albumId);
    }

    public CompletableFuture<CommentPage> getComments(final long songId, final int page, final int pageSize) {
        return apiClient.getComments(songId, page, pageSize);
    }

    public CompletableFuture<Long> getPublishTime(final long songId) {
        return apiClient.getPublishTime(songId);
    }

    public CompletableFuture<JsonObject> getServiceStats() {
        return apiClient.getServiceStats();
    }

    private synchronized void startCurrent(final long seekMillis) {
        if (closed || queueIndex < 0 || queueIndex >= queue.size()) return;
        final long generation = operationGeneration.incrementAndGet();
        currentSong = queue.get(queueIndex);
        final Song playingSong = currentSong;
        currentAudioPath = null;
        currentArtworkPath = null;
        lyricTimeline = LyricTimeline.empty();
        error = "";
        pausedPositionMillis = Math.max(0L, seekMillis);
        state = pauseRequested ? PlaybackState.PAUSED : PlaybackState.RESOLVING;
        registerIsland();
        updateSnapshot(seekMillis);
        loadLyrics(playingSong.id(), generation);

        final MusicQuality quality = settingsRepository.get().quality();
        apiClient.resolveAudio(playingSong.id(), quality)
                .thenCompose(cache::resolve)
                .thenCompose(path -> {
                    synchronized (this) {
                        if (operationGeneration.get() != generation || closed) {
                            return CompletableFuture.failedFuture(new SupersededPlaybackException());
                        }
                        currentAudioPath = path;
                        state = pauseRequested ? PlaybackState.PAUSED : PlaybackState.BUFFERING;
                        updateSnapshot(seekMillis);
                    }
                    artworkCache.resolve(playingSong, path).thenAccept(artwork -> {
                        if (operationGeneration.get() == generation && !closed) currentArtworkPath = artwork;
                    });
                    if (pauseRequested) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return player.play(path, seekMillis, volume);
                })
                .whenComplete((ignored, throwable) -> {
                    synchronized (this) {
                        if (operationGeneration.get() != generation || closed) return;
                        if (throwable != null) {
                            if (!(unwrap(throwable) instanceof SupersededPlaybackException)) fail(throwable);
                            return;
                        }
                        if (pauseRequested) {
                            state = PlaybackState.PAUSED;
                            DynamicIslandElement.removeTrigger(islandTrigger);
                            updateSnapshot(pausedPositionMillis);
                            return;
                        }
                        pausedPositionMillis = 0L;
                        state = PlaybackState.PLAYING;
                        library.recordPlay(playingSong);
                        updateSnapshot();
                    }
                });
    }

    private synchronized void monitorPlayback() {
        if (closed) return;
        if (state == PlaybackState.PLAYING && !pauseRequested && currentSong != null) {
            final long position = player.getPositionMillis();
            final long duration = currentSong.durationMillis();
            final boolean sourceEnded = player.hasEnded();
            final boolean durationReached = duration > 0 && position >= duration + 1000;
            final boolean sourceEndedNearTrackEnd = sourceEnded
                    && (duration <= 0 || position >= Math.max(0, duration - 2000));
            if (durationReached || sourceEndedNearTrackEnd) {
                next();
                return;
            }
            if (sourceEnded) {
                pausePlayback();
            }
        }
        if (state == PlaybackState.PLAYING || state == PlaybackState.PAUSED) updateSnapshot();
    }

    private void fail(final Throwable throwable) {
        pauseRequested = false;
        state = PlaybackState.ERROR;
        error = readableError(throwable);
        player.stop();
        registerIsland();
        updateSnapshot();
    }

    private void loadLyrics(final long songId, final long generation) {
        apiClient.getLyrics(songId).thenAccept(document -> {
            if (operationGeneration.get() == generation && !closed) {
                lyricTimeline = LyricTimeline.parse(document);
            }
        }).exceptionally(ignored -> null);
    }

    private static long parsePlaylistId(final String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Enter a playlist ID or URL");
        final String value = input.trim();
        if (value.chars().allMatch(Character::isDigit)) return Long.parseLong(value);
        for (final String expression : List.of("[?&]id=(\\d+)", "/playlist/(\\d+)", "playlist/(\\d+)")) {
            final java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(expression).matcher(value);
            if (matcher.find()) return Long.parseLong(matcher.group(1));
        }
        throw new IllegalArgumentException("Unable to find a playlist ID in that value");
    }

    private void registerIsland() {
        if (settingsRepository.get().showDynamicIsland() && !pauseRequested && state != PlaybackState.PAUSED) {
            DynamicIslandElement.addTrigger(islandTrigger);
        } else {
            DynamicIslandElement.removeTrigger(islandTrigger);
        }
    }

    private void updateSnapshot() {
        updateSnapshot(player.getPositionMillis());
    }

    private void updateSnapshot(final long position) {
        snapshot = new PlaybackSnapshot(
                state,
                currentSong,
                currentSong == null ? 0 : Math.min(position, currentSong.durationMillis()),
                currentSong == null ? 0 : currentSong.durationMillis(),
                volume,
                queueIndex,
                queue.size(),
                repeatMode,
                shuffle,
                error
        );
    }

    private void persistSettings() {
        final MusicSettingsRepository.Settings old = settingsRepository.get();
        settingsRepository.update(new MusicSettingsRepository.Settings(
                old.schemaVersion(), old.quality(), volume, old.cacheLimitBytes(), repeatMode, shuffle, old.showDynamicIsland()
        ));
    }

    private synchronized void pausePlayback() {
        if (closed || currentSong == null || state == PlaybackState.IDLE || state == PlaybackState.ERROR) {
            return;
        }
        final long position = player.isPlaying() ? player.getPositionMillis() : snapshot.positionMillis();
        pauseRequested = true;
        pausedPositionMillis = Math.max(0L, position);
        operationGeneration.incrementAndGet();
        player.stop();
        state = PlaybackState.PAUSED;
        DynamicIslandElement.removeTrigger(islandTrigger);
        updateSnapshot(pausedPositionMillis);
    }

    private synchronized void resumePlayback() {
        if (closed || currentSong == null) {
            return;
        }
        final long position = pausedPositionMillis;
        pauseRequested = false;
        if (currentAudioPath == null) {
            startCurrent(position);
            return;
        }
        state = PlaybackState.SEEKING;
        registerIsland();
        updateSnapshot(position);
        seek(position);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        operationGeneration.incrementAndGet();
        DynamicIslandElement.removeTrigger(islandTrigger);
        player.close();
        monitor.shutdownNow();
        ioExecutor.shutdownNow();
    }

    private static Throwable unwrap(final Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String readableError(final Throwable throwable) {
        final Throwable cause = unwrap(throwable);
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static final class SupersededPlaybackException extends RuntimeException {
    }
}
