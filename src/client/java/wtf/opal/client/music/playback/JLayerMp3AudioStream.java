package wtf.opal.client.music.playback;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.sound.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JLayerMp3AudioStream implements AudioStream {
    private final InputStream input;
    private final Bitstream bitstream;
    private final Decoder decoder = new Decoder();
    private final AudioFormat format;

    private byte[] pending;
    private int pendingOffset;
    private boolean endOfStream;

    public JLayerMp3AudioStream(final Path path, final long seekMillis) throws IOException {
        this.input = new BufferedInputStream(Files.newInputStream(path), 64 * 1024);
        this.bitstream = new Bitstream(input);
        try {
            final DecodedFrame first = decodeFrame();
            if (first == null) throw new IOException("MP3 contains no audio frames");
            this.format = new AudioFormat(first.sampleRate, 16, first.channels, true, false);

            long skippedMillis = 0;
            DecodedFrame current = first;
            while (current != null) {
                final long frameMillis = Math.max(1, current.sampleCount * 1000L / current.channels / current.sampleRate);
                if (skippedMillis + frameMillis > Math.max(0, seekMillis)) {
                    final long insideMillis = Math.max(0, seekMillis - skippedMillis);
                    final int skipSamples = (int) Math.min(
                            current.sampleCount,
                            insideMillis * current.sampleRate * current.channels / 1000L
                    );
                    this.pending = toPcm(current.samples, skipSamples, current.sampleCount);
                    break;
                }
                skippedMillis += frameMillis;
                current = decodeFrame();
            }
            if (current == null) endOfStream = true;
        } catch (final BitstreamException | DecoderException exception) {
            close();
            throw new IOException("Unable to initialize MP3 decoder", exception);
        }
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(final int size) throws IOException {
        if (size <= 0) return null;
        final ByteBuffer output = ByteBuffer.allocateDirect(size);
        try {
            while (output.hasRemaining()) {
                if (pending != null && pendingOffset < pending.length) {
                    final int amount = Math.min(output.remaining(), pending.length - pendingOffset);
                    output.put(pending, pendingOffset, amount);
                    pendingOffset += amount;
                    if (pendingOffset >= pending.length) {
                        pending = null;
                        pendingOffset = 0;
                    }
                    continue;
                }
                if (endOfStream) break;
                final DecodedFrame frame = decodeFrame();
                if (frame == null) {
                    endOfStream = true;
                    break;
                }
                if (frame.sampleRate != (int) format.getSampleRate() || frame.channels != format.getChannels()) {
                    throw new IOException("MP3 format changed mid-stream");
                }
                pending = toPcm(frame.samples, 0, frame.sampleCount);
            }
        } catch (final BitstreamException | DecoderException exception) {
            throw new IOException("Unable to decode MP3 frame", exception);
        }
        if (output.position() == 0) return null;
        output.flip();
        return output;
    }

    private DecodedFrame decodeFrame() throws BitstreamException, DecoderException {
        final Header header = bitstream.readFrame();
        if (header == null) return null;
        try {
            final SampleBuffer samples = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            final short[] copy = new short[samples.getBufferLength()];
            System.arraycopy(samples.getBuffer(), 0, copy, 0, copy.length);
            return new DecodedFrame(copy, copy.length, samples.getSampleFrequency(), samples.getChannelCount());
        } finally {
            bitstream.closeFrame();
        }
    }

    private static byte[] toPcm(final short[] samples, final int start, final int end) {
        final int safeStart = Math.clamp(start, 0, end);
        final byte[] bytes = new byte[(end - safeStart) * 2];
        int output = 0;
        for (int i = safeStart; i < end; i++) {
            final short sample = samples[i];
            bytes[output++] = (byte) sample;
            bytes[output++] = (byte) (sample >>> 8);
        }
        return bytes;
    }

    @Override
    public void close() throws IOException {
        try {
            bitstream.close();
        } catch (final BitstreamException exception) {
            throw new IOException(exception);
        } finally {
            input.close();
        }
    }

    private record DecodedFrame(short[] samples, int sampleCount, int sampleRate, int channels) {
    }
}
