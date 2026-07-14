package wtf.opal.client.music.cache;

import wtf.opal.client.music.model.Song;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class MusicArtworkCache {
    private static final long MAX_ARTWORK_BYTES = 16L * 1024L * 1024L;

    private final Path directory;
    private final HttpClient httpClient;
    private final Executor executor;

    public MusicArtworkCache(final Path directory, final HttpClient httpClient, final Executor executor) {
        this.directory = directory;
        this.httpClient = httpClient;
        this.executor = executor;
    }

    public CompletableFuture<Path> resolve(final Song song, final Path audioPath) {
        return CompletableFuture.supplyAsync(() -> {
            final Path existing = findExisting(song.id());
            if (existing != null) return existing;
            return EmbeddedArtworkExtractor.extract(audioPath, directory, song.id());
        }, executor).thenCompose(path -> path != null
                ? CompletableFuture.completedFuture(path)
                : downloadRemote(song));
    }

    private CompletableFuture<Path> downloadRemote(final Song song) {
        if (song.artworkUrl().isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        final URI uri;
        try {
            uri = URI.create(song.artworkUrl());
        } catch (final IllegalArgumentException ignored) {
            return CompletableFuture.completedFuture(null);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            return CompletableFuture.completedFuture(null);
        }

        final HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*;q=0.5")
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApplyAsync(response -> saveRemote(song.id(), response), executor)
                .exceptionally(ignored -> null);
    }

    private Path saveRemote(final long songId, final HttpResponse<InputStream> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            closeQuietly(response.body());
            return null;
        }
        final String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase();
        if (!contentType.isBlank() && !contentType.startsWith("image/")) {
            closeQuietly(response.body());
            return null;
        }
        try {
            Files.createDirectories(directory);
            final Path target = directory.resolve(songId + "-remote.img");
            final Path temporary = Files.createTempFile(directory, ".artwork-", ".tmp");
            try (InputStream input = response.body()) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.size(temporary) <= 0 || Files.size(temporary) > MAX_ARTWORK_BYTES) {
                Files.deleteIfExists(temporary);
                return null;
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (final IOException ignored) {
            return null;
        }
    }

    private Path findExisting(final long songId) {
        if (!Files.isDirectory(directory)) return null;
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(songId + "-"))
                    .findFirst()
                    .orElse(null);
        } catch (final IOException ignored) {
            return null;
        }
    }

    private static void closeQuietly(final InputStream input) {
        try {
            input.close();
        } catch (final IOException ignored) {
        }
    }
}
