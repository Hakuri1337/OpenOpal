package wtf.opal.client.music.cache;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

final class EmbeddedArtworkExtractor {
    private static final int MAX_TAG_BYTES = 32 * 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 16 * 1024 * 1024;

    private EmbeddedArtworkExtractor() {
    }

    static Path extract(final Path audioPath, final Path artworkDirectory, final long songId) {
        try (RandomAccessFile file = new RandomAccessFile(audioPath.toFile(), "r")) {
            final byte[] header = new byte[10];
            if (file.read(header) != header.length || header[0] != 'I' || header[1] != 'D' || header[2] != '3') {
                return null;
            }

            final int version = header[3] & 0xFF;
            if (version < 2 || version > 4) {
                return null;
            }
            final int tagSize = syncSafeInt(header, 6);
            if (tagSize <= 0 || tagSize > MAX_TAG_BYTES || tagSize > file.length() - 10L) {
                return null;
            }

            final byte[] tag = new byte[tagSize];
            file.readFully(tag);
            int frameOffset = 0;
            if ((header[5] & 0x40) != 0 && tag.length >= 4) {
                frameOffset = version == 3 ? 4 + bigEndianInt(tag, 0) : syncSafeInt(tag, 0);
                if (frameOffset < 0 || frameOffset >= tag.length) return null;
            }
            final Picture picture = findPicture(tag, version, frameOffset, (header[5] & 0x80) != 0);
            if (picture == null || picture.data.length == 0 || picture.data.length > MAX_IMAGE_BYTES) {
                return null;
            }

            Files.createDirectories(artworkDirectory);
            final String extension = imageExtension(picture.data, picture.mimeType);
            final Path target = artworkDirectory.resolve(songId + "-embedded." + extension);
            final Path temporary = Files.createTempFile(artworkDirectory, ".artwork-", ".tmp");
            Files.write(temporary, picture.data);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (final IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static Picture findPicture(final byte[] tag, final int version, final int frameOffset, final boolean unsynchronized) {
        int offset = frameOffset;
        while (offset < tag.length) {
            final int headerSize = version == 2 ? 6 : 10;
            if (offset + headerSize > tag.length) {
                break;
            }

            final String id = ascii(tag, offset, version == 2 ? 3 : 4);
            if (id.chars().allMatch(value -> value == 0)) {
                break;
            }
            final int size = version == 2
                    ? ((tag[offset + 3] & 0xFF) << 16) | ((tag[offset + 4] & 0xFF) << 8) | (tag[offset + 5] & 0xFF)
                    : version == 4 ? syncSafeInt(tag, offset + 4) : bigEndianInt(tag, offset + 4);
            if (size <= 0 || offset + headerSize + size > tag.length) {
                break;
            }

            if ((version == 2 && "PIC".equals(id)) || (version >= 3 && "APIC".equals(id))) {
                final byte[] frame = Arrays.copyOfRange(tag, offset + headerSize, offset + headerSize + size);
                final Picture picture = parsePicture(frame, version);
                if (picture == null || !unsynchronized) return picture;
                return new Picture(picture.mimeType, removeUnsynchronization(picture.data));
            }
            offset += headerSize + size;
        }
        return null;
    }

    private static Picture parsePicture(final byte[] frame, final int version) {
        if (frame.length < 6) {
            return null;
        }
        final int encoding = frame[0] & 0xFF;
        int offset = 1;
        final String mime;
        if (version == 2) {
            mime = ascii(frame, offset, 3);
            offset += 3;
        } else {
            final int mimeEnd = findTerminator(frame, offset, false);
            if (mimeEnd < 0) return null;
            mime = ascii(frame, offset, mimeEnd - offset);
            offset = mimeEnd + 1;
        }
        if (offset >= frame.length) return null;
        offset++; // Picture type.

        final boolean wideTerminator = encoding == 1 || encoding == 2;
        final int descriptionEnd = findTerminator(frame, offset, wideTerminator);
        if (descriptionEnd < 0) return null;
        offset = descriptionEnd + (wideTerminator ? 2 : 1);
        if (offset >= frame.length) return null;
        return new Picture(mime, Arrays.copyOfRange(frame, offset, frame.length));
    }

    private static int findTerminator(final byte[] bytes, final int start, final boolean wide) {
        if (!wide) {
            for (int index = start; index < bytes.length; index++) {
                if (bytes[index] == 0) return index;
            }
            return -1;
        }
        for (int index = start; index + 1 < bytes.length; index += 2) {
            if (bytes[index] == 0 && bytes[index + 1] == 0) return index;
        }
        return -1;
    }

    private static int syncSafeInt(final byte[] bytes, final int offset) {
        return ((bytes[offset] & 0x7F) << 21)
                | ((bytes[offset + 1] & 0x7F) << 14)
                | ((bytes[offset + 2] & 0x7F) << 7)
                | (bytes[offset + 3] & 0x7F);
    }

    private static int bigEndianInt(final byte[] bytes, final int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static String ascii(final byte[] bytes, final int offset, final int length) {
        return new String(bytes, offset, Math.max(0, length), java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private static String imageExtension(final byte[] bytes, final String mime) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "png";
        }
        if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return "jpg";
        }
        return mime != null && mime.toLowerCase().contains("png") ? "png" : "jpg";
    }

    private static byte[] removeUnsynchronization(final byte[] bytes) {
        final byte[] output = new byte[bytes.length];
        int write = 0;
        for (int read = 0; read < bytes.length; read++) {
            output[write++] = bytes[read];
            if ((bytes[read] & 0xFF) == 0xFF && read + 1 < bytes.length && bytes[read + 1] == 0) read++;
        }
        return Arrays.copyOf(output, write);
    }

    private record Picture(String mimeType, byte[] data) {
    }
}
