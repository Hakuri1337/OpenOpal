package wtf.opal.client.renderer.menu;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import wtf.opal.client.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static wtf.opal.client.Constants.mc;

public final class MenuVideoBackground {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESOURCE_PATH = "/assets/opal/videos/background_hd.opv";
    private static final int PACK_MAGIC = 0x4F505646;
    private static final int PACK_VERSION = 1;
    private static final int PACK_HEADER_BYTES = 24;
    private static final int PACK_ENTRY_BYTES = 8;
    private static final Identifier VIDEO_TEXTURE = Identifier.of("opal", "dynamic/menu_background");
    private static final Identifier FALLBACK_TEXTURE = Identifier.of("opal", "images/mainmenubg.png");
    private static final AtomicReference<VideoFrame> PENDING_FRAME = new AtomicReference<>();

    private static volatile boolean active;
    private static volatile long retryAfter;
    private static volatile boolean playbackInfoLogged;
    private static Thread decoderThread;
    private static NativeImageBackedTexture texture;
    private static int textureWidth;
    private static int textureHeight;

    private MenuVideoBackground() {
    }

    public static void setActive(final boolean value) {
        active = value;
        if (!value) {
            closeFrame(PENDING_FRAME.getAndSet(null));
            return;
        }
        ensureDecoderThread();
    }

    public static void render(final DrawContext context, final int width, final int height) {
        setActive(true);
        uploadPendingFrame();
        if (texture != null && textureWidth > 0 && textureHeight > 0) {
            drawCover(context, VIDEO_TEXTURE, width, height, textureWidth, textureHeight);
        } else {
            drawCover(context, FALLBACK_TEXTURE, width, height, 1920, 1080);
        }
    }

    private static synchronized void ensureDecoderThread() {
        if (decoderThread != null && decoderThread.isAlive()) {
            return;
        }
        decoderThread = new Thread(MenuVideoBackground::decodeLoop, "OpenOpal HD Menu Video");
        decoderThread.setDaemon(true);
        decoderThread.setPriority(Thread.NORM_PRIORITY - 1);
        decoderThread.start();
    }

    private static void decodeLoop() {
        while (true) {
            if (!active || System.currentTimeMillis() < retryAfter) {
                LockSupport.parkNanos(100_000_000L);
                continue;
            }

            try {
                decodePack(extractVideoPack());
            } catch (Exception exception) {
                LOGGER.warn("Unable to decode the OpenOpal HD menu background; using the static fallback", exception);
                retryAfter = System.currentTimeMillis() + 5000L;
            }
        }
    }

    private static void decodePack(final Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            final ByteBuffer header = ByteBuffer.allocate(PACK_HEADER_BYTES).order(ByteOrder.BIG_ENDIAN);
            readFully(channel, header);
            header.flip();
            if (header.getInt() != PACK_MAGIC || header.getInt() != PACK_VERSION) {
                throw new IOException("Unsupported OpenOpal video pack");
            }
            final int width = header.getInt();
            final int height = header.getInt();
            final int fps = header.getInt();
            final int frameCount = header.getInt();
            if (width <= 0 || height <= 0 || fps <= 0 || frameCount <= 0 || frameCount > 10_000) {
                throw new IOException("Invalid OpenOpal video pack dimensions");
            }

            final ByteBuffer table = ByteBuffer.allocate(frameCount * PACK_ENTRY_BYTES).order(ByteOrder.BIG_ENDIAN);
            readFully(channel, table);
            table.flip();
            final long[] offsets = new long[frameCount];
            final int[] lengths = new int[frameCount];
            for (int index = 0; index < frameCount; index++) {
                offsets[index] = Integer.toUnsignedLong(table.getInt());
                lengths[index] = table.getInt();
                if (lengths[index] <= 0 || offsets[index] + lengths[index] > channel.size()) {
                    throw new IOException("Invalid OpenOpal video frame table");
                }
            }

            if (!playbackInfoLogged) {
                playbackInfoLogged = true;
                LOGGER.info("OpenOpal HD menu video: {}x{} / {} FPS / {} frames", width, height, fps, frameCount);
            }

            final long frameInterval = 1_000_000_000L / fps;
            final long playbackStart = System.nanoTime();
            long sequence = 0L;
            int lastFrame = -1;
            while (active) {
                final long now = System.nanoTime();
                final long currentSequence = Math.max(sequence, (now - playbackStart) / frameInterval);
                final long targetTime = playbackStart + currentSequence * frameInterval;
                final long wait = targetTime - now;
                if (wait > 0L) {
                    LockSupport.parkNanos(wait);
                }
                if (!active) {
                    return;
                }

                final int frameIndex = (int) (currentSequence % frameCount);
                sequence = currentSequence + 1L;
                if (frameIndex == lastFrame) {
                    continue;
                }
                lastFrame = frameIndex;
                final NativeImage image = readFrame(channel, offsets[frameIndex], lengths[frameIndex]);
                if (!active) {
                    image.close();
                    return;
                }
                closeFrame(PENDING_FRAME.getAndSet(new VideoFrame(image)));
            }
        }
    }

    private static NativeImage readFrame(final FileChannel channel, final long offset, final int length) throws IOException {
        final ByteBuffer encoded = MemoryUtil.memAlloc(length);
        try {
            channel.position(offset);
            readFully(channel, encoded);
            encoded.flip();
            final IntBuffer width = MemoryUtil.memAllocInt(1);
            final IntBuffer height = MemoryUtil.memAllocInt(1);
            final IntBuffer channels = MemoryUtil.memAllocInt(1);
            try {
                final ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4);
                if (pixels == null) {
                    throw new IOException("Unable to decode OpenOpal video frame: " + STBImage.stbi_failure_reason());
                }
                return new NativeImage(
                        NativeImage.Format.RGBA,
                        width.get(0),
                        height.get(0),
                        true,
                        MemoryUtil.memAddress(pixels)
                );
            } finally {
                MemoryUtil.memFree(width);
                MemoryUtil.memFree(height);
                MemoryUtil.memFree(channels);
            }
        } finally {
            MemoryUtil.memFree(encoded);
        }
    }

    private static void readFully(final FileChannel channel, final ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new IOException("Unexpected end of OpenOpal video pack");
            }
        }
    }

    private static Path extractVideoPack() throws IOException {
        final URL resource = MenuVideoBackground.class.getResource(RESOURCE_PATH);
        if (resource == null) {
            throw new IOException("Missing " + RESOURCE_PATH);
        }

        final Path cacheDirectory = Constants.DIRECTORY.toPath().resolve("cache");
        final Path cachedVideo = cacheDirectory.resolve("menu-background-hd.opv");
        final long resourceLength = resource.openConnection().getContentLengthLong();
        if (Files.isRegularFile(cachedVideo) && resourceLength > 0L && Files.size(cachedVideo) == resourceLength) {
            return cachedVideo;
        }

        Files.createDirectories(cacheDirectory);
        final Path temporaryVideo = cacheDirectory.resolve("menu-background-hd.opv.part");
        try (InputStream input = resource.openStream()) {
            Files.copy(input, temporaryVideo, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temporaryVideo, cachedVideo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporaryVideo, cachedVideo, StandardCopyOption.REPLACE_EXISTING);
        }
        return cachedVideo;
    }

    private static void uploadPendingFrame() {
        final VideoFrame frame = PENDING_FRAME.getAndSet(null);
        if (frame == null) {
            return;
        }

        boolean transferred = false;
        try {
            final NativeImage image = frame.image;
            final int width = image.getWidth();
            final int height = image.getHeight();
            if (texture == null || textureWidth != width || textureHeight != height) {
                if (texture != null) {
                    mc.getTextureManager().destroyTexture(VIDEO_TEXTURE);
                }
                textureWidth = width;
                textureHeight = height;
                texture = new NativeImageBackedTexture(() -> "OpenOpal HD menu background", image);
                texture.setFilter(true, false);
                mc.getTextureManager().registerTexture(VIDEO_TEXTURE, texture);
            } else {
                texture.setImage(image);
                texture.upload();
            }
            transferred = true;
        } finally {
            if (!transferred) {
                closeFrame(frame);
            }
        }
    }

    private static void closeFrame(final VideoFrame frame) {
        if (frame != null) {
            frame.image.close();
        }
    }

    private static void drawCover(final DrawContext context, final Identifier textureIdentifier,
                                  final int width, final int height, final int sourceWidth, final int sourceHeight) {
        final double screenAspect = width / (double) Math.max(1, height);
        final double sourceAspect = sourceWidth / (double) sourceHeight;
        int regionWidth = sourceWidth;
        int regionHeight = sourceHeight;
        float u = 0F;
        float v = 0F;

        if (sourceAspect > screenAspect) {
            regionWidth = Math.max(1, (int) Math.round(sourceHeight * screenAspect));
            u = (sourceWidth - regionWidth) / 2F;
        } else if (sourceAspect < screenAspect) {
            regionHeight = Math.max(1, (int) Math.round(sourceWidth / screenAspect));
            v = (sourceHeight - regionHeight) / 2F;
        }

        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                textureIdentifier,
                0,
                0,
                u,
                v,
                width,
                height,
                regionWidth,
                regionHeight,
                sourceWidth,
                sourceHeight
        );
    }

    private record VideoFrame(NativeImage image) {
    }
}
