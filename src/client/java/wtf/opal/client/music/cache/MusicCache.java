package wtf.opal.client.music.cache;

import wtf.opal.client.music.model.AudioSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class MusicCache {
    private final Path audioDirectory;
    private final HttpClient httpClient;
    private final Executor ioExecutor;
    private volatile long maximumBytes = 2L * 1024 * 1024 * 1024;

    public MusicCache(final Path directory, final HttpClient httpClient, final Executor ioExecutor) {
        this.audioDirectory = directory.resolve("audio");
        this.httpClient = httpClient;
        this.ioExecutor = ioExecutor;
    }

    public CompletableFuture<Path> resolve(final AudioSource source) {
        final Path target = pathFor(source);
        if (Files.isRegularFile(target)) {
            return CompletableFuture.completedFuture(target);
        }
        final HttpRequest request = HttpRequest.newBuilder(source.uri())
                .timeout(Duration.ofMinutes(2))
                .header("Accept", "audio/mpeg,audio/*;q=0.9,*/*;q=0.1")
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApplyAsync(response -> download(response, source, target), ioExecutor);
    }

    public void setMaximumBytes(final long maximumBytes) {
        this.maximumBytes = Math.max(128L * 1024 * 1024, maximumBytes);
    }

    private Path download(final HttpResponse<InputStream> response, final AudioSource source, final Path target) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            closeQuietly(response.body());
            throw new IllegalStateException("Audio download returned HTTP " + response.statusCode());
        }
        try {
            Files.createDirectories(audioDirectory);
            final Path temporary = Files.createTempFile(audioDirectory, ".download-", ".tmp");
            try (InputStream input = response.body()) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            if (source.size() > 0 && Files.size(temporary) != source.size()) {
                Files.deleteIfExists(temporary);
                throw new IOException("Downloaded audio size does not match API metadata");
            }
            if (!source.md5().isBlank() && !source.md5().equalsIgnoreCase(md5(temporary))) {
                Files.deleteIfExists(temporary);
                throw new IOException("Downloaded audio failed MD5 verification");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (final AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            evictIfNeeded();
            return target;
        } catch (final IOException exception) {
            throw new IllegalStateException("Unable to cache audio", exception);
        }
    }

    private Path pathFor(final AudioSource source) {
        final String hash = source.md5().isBlank() ? "unknown" : source.md5();
        return audioDirectory.resolve(source.songId() + "-" + source.actualQuality().getApiName() + "-" + hash + ".mp3");
    }

    private void evictIfNeeded() throws IOException {
        if (!Files.isDirectory(audioDirectory)) return;
        try (var files = Files.list(audioDirectory)) {
            final var ordered = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".mp3"))
                    .sorted(Comparator.comparingLong(MusicCache::lastModified))
                    .toList();
            long total = 0;
            for (final Path path : ordered) total += Files.size(path);
            for (final Path path : ordered) {
                if (total <= maximumBytes) break;
                final long size = Files.size(path);
                if (Files.deleteIfExists(path)) total -= size;
            }
        }
    }

    private static String md5(final Path path) throws IOException {
        try {
            final MessageDigest digest = MessageDigest.getInstance("MD5");
            try (InputStream input = Files.newInputStream(path)) {
                final byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (final NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static long lastModified(final Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (final IOException ignored) {
            return 0;
        }
    }

    private static void closeQuietly(final InputStream input) {
        try {
            input.close();
        } catch (final IOException ignored) {
        }
    }
}
