package wtf.opal.client.music.playback;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundEngine;
import wtf.opal.mixin.SoundManagerAccessor;
import wtf.opal.mixin.SoundSystemAccessor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static wtf.opal.client.Constants.mc;

public final class OpenAlMusicPlayer implements AutoCloseable {
    private final AtomicLong generation = new AtomicLong();

    private volatile Channel.SourceManager sourceManager;
    private volatile long basePositionMillis;
    private volatile long startedAtNanos;
    private volatile float volume = 0.7F;
    private volatile boolean playing;
    private volatile boolean paused;

    public CompletableFuture<Void> play(final Path path, final long seekMillis, final float requestedVolume) {
        final long currentGeneration = generation.incrementAndGet();
        stopCurrentSource();

        final JLayerMp3AudioStream stream;
        try {
            stream = new JLayerMp3AudioStream(path, seekMillis);
        } catch (final IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        final Channel channel = ((SoundSystemAccessor) ((SoundManagerAccessor) mc.getSoundManager())
                .opal$getSoundSystem()).opal$getChannel();
        return channel.createSource(SoundEngine.RunMode.STREAMING).thenCompose(manager -> {
            if (manager == null) {
                closeQuietly(stream);
                return CompletableFuture.failedFuture(new IllegalStateException("No OpenAL streaming source is available"));
            }
            if (generation.get() != currentGeneration) {
                manager.run(source -> source.stop());
                closeQuietly(stream);
                return CompletableFuture.completedFuture(null);
            }
            final CompletableFuture<Void> started = new CompletableFuture<>();
            sourceManager = manager;
            volume = Math.clamp(requestedVolume, 0.0F, 1.0F);
            basePositionMillis = Math.max(0, seekMillis);
            manager.run(source -> {
                try {
                    source.setRelative(true);
                    source.disableAttenuation();
                    source.setLooping(false);
                    source.setPitch(1.0F);
                    source.setVolume(volume);
                    source.setStream(stream);
                    source.play();
                    startedAtNanos = System.nanoTime();
                    playing = true;
                    paused = false;
                    started.complete(null);
                } catch (final RuntimeException exception) {
                    source.stop();
                    started.completeExceptionally(exception);
                }
            });
            return started;
        });
    }

    public void pause() {
        final Channel.SourceManager manager = sourceManager;
        if (manager == null || !playing || paused) return;
        basePositionMillis = getPositionMillis();
        paused = true;
        manager.run(source -> source.pause());
    }

    public void resume() {
        final Channel.SourceManager manager = sourceManager;
        if (manager == null || !playing || !paused) return;
        startedAtNanos = System.nanoTime();
        paused = false;
        manager.run(source -> source.resume());
    }

    public void setVolume(final float value) {
        volume = Math.clamp(value, 0.0F, 1.0F);
        final Channel.SourceManager manager = sourceManager;
        if (manager != null) manager.run(source -> source.setVolume(volume));
    }

    public long getPositionMillis() {
        if (!playing || paused) return basePositionMillis;
        return basePositionMillis + Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    public boolean hasEnded() {
        final Channel.SourceManager manager = sourceManager;
        return playing && manager != null && manager.isStopped();
    }

    public boolean isPlaying() {
        return playing;
    }

    public void stop() {
        generation.incrementAndGet();
        stopCurrentSource();
        basePositionMillis = 0;
    }

    private void stopCurrentSource() {
        final Channel.SourceManager manager = sourceManager;
        sourceManager = null;
        playing = false;
        paused = false;
        if (manager != null && !manager.isStopped()) {
            manager.run(source -> source.stop());
        }
    }

    @Override
    public void close() {
        stop();
    }

    private static void closeQuietly(final JLayerMp3AudioStream stream) {
        try {
            stream.close();
        } catch (final IOException ignored) {
        }
    }
}
