package wtf.opal.client.music.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import wtf.opal.client.music.model.MusicQuality;
import wtf.opal.client.music.playback.RepeatMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class MusicSettingsRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private Settings settings;

    public MusicSettingsRepository(final Path directory) {
        this.file = directory.resolve("settings.json");
        this.settings = load();
    }

    public synchronized Settings get() {
        return settings;
    }

    public synchronized void update(final Settings value) {
        settings = value.normalized();
        save();
    }

    private Settings load() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) return Settings.defaults();
            final Settings loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Settings.class);
            return loaded == null ? Settings.defaults() : loaded.normalized();
        } catch (final IOException | RuntimeException ignored) {
            return Settings.defaults();
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            final Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(settings), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (final AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (final IOException exception) {
            throw new IllegalStateException("Unable to save music settings", exception);
        }
    }

    public record Settings(
            int schemaVersion,
            MusicQuality quality,
            float volume,
            long cacheLimitBytes,
            RepeatMode repeatMode,
            boolean shuffle,
            boolean showDynamicIsland
    ) {
        public static Settings defaults() {
            return new Settings(1, MusicQuality.EXHIGH, 0.7F, 2L * 1024 * 1024 * 1024, RepeatMode.OFF, false, true);
        }

        private Settings normalized() {
            return new Settings(
                    1,
                    quality == null ? MusicQuality.EXHIGH : quality,
                    Math.clamp(volume, 0.0F, 1.0F),
                    Math.max(128L * 1024 * 1024, cacheLimitBytes),
                    repeatMode == null ? RepeatMode.OFF : repeatMode,
                    shuffle,
                    showDynamicIsland
            );
        }
    }
}
