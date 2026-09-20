package com.ulticode.common.storage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Shared sniffing and decode bounds for user-uploaded images.
 *
 * <p>Both the App avatar endpoint and the Admin avatar adapter must reject a file whose compressed size looks fine but
 * whose decoded raster would exhaust the heap. Dimension metadata is therefore read through {@link ImageReader} before
 * any decode, and a decode only happens inside the pixel budget. Keeping one implementation here stops the two
 * services from drifting apart.
 */
public final class ImageContent {

    /** Largest accepted width and height. */
    public static final int MAX_DIMENSION = 4096;

    /** Largest accepted decoded raster, in pixels. */
    public static final long MAX_PIXELS = (long) MAX_DIMENSION * MAX_DIMENSION;

    private ImageContent() {
    }

    /** A sniffed image: canonical file extension (without dot) and content type. */
    public record Detected(String extension, String contentType) {
    }

    /**
     * Sniffs {@code content} by magic bytes and verifies it decodes inside the pixel budget.
     *
     * @return the detected image, or {@code null} when the content is not one of the supported image types
     * @throws IllegalArgumentException when the image exceeds {@link #MAX_PIXELS} or its dimensions cannot be read
     */
    public static Detected detect(byte[] content) {
        if (content == null || content.length == 0) {
            return null;
        }
        if (isPng(content)) {
            return verified(content, "png", "image/png");
        }
        if (isJpeg(content)) {
            return verified(content, "jpg", "image/jpeg");
        }
        if (isGif(content)) {
            return verified(content, "gif", "image/gif");
        }
        if (isWebp(content)) {
            assertWithinPixelBudget(content);
            return new Detected("webp", "image/webp");
        }
        return null;
    }

    /**
     * Verifies the decoded raster stays inside {@link #MAX_PIXELS} without decoding it.
     *
     * @throws IllegalArgumentException when the dimensions are unreadable or above the budget
     */
    public static void assertWithinPixelBudget(byte[] content) {
        long[] dimensions = readDimensions(content);
        if (dimensions == null) {
            throw new IllegalArgumentException("Image dimensions could not be read");
        }
        if (dimensions[0] <= 0
                || dimensions[1] <= 0
                || dimensions[0] > MAX_DIMENSION
                || dimensions[1] > MAX_DIMENSION
                || dimensions[0] > MAX_PIXELS / dimensions[1]) {
            throw new IllegalArgumentException(
                    "Image dimensions exceed " + MAX_DIMENSION + "x" + MAX_DIMENSION + " pixel limit");
        }
    }

    private static Detected verified(byte[] content, String extension, String contentType) {
        assertWithinPixelBudget(content);
        if (!decodes(content)) {
            return null;
        }
        return new Detected(extension, contentType);
    }

    /** Reads only the header metadata, so an oversized image is rejected before any raster allocation. */
    private static long[] readDimensions(byte[] content) {
        if (content != null && isWebp(content)) {
            return readWebpDimensions(content);
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new long[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static long[] readWebpDimensions(byte[] content) {
        if (content.length < 12) {
            return null;
        }
        long riffSize = unsignedIntLittleEndian(content, 4);
        long riffEnd = 8L + riffSize;
        if (riffSize < 4 || riffEnd != content.length || riffEnd < 12) {
            return null;
        }
        int chunkOffset = 12;
        long[] extendedDimensions = null;
        long[] frameDimensions = null;
        boolean hasFramePayload = false;
        while (chunkOffset < riffEnd) {
            long remaining = riffEnd - chunkOffset;
            if (remaining < 8) {
                return null;
            }
            long chunkLength = unsignedIntLittleEndian(content, chunkOffset + 4);
            long chunkEnd = chunkOffset + 8L + chunkLength;
            long nextChunkOffset = chunkEnd + (chunkLength & 1L);
            if (chunkEnd < chunkOffset || nextChunkOffset < chunkEnd || nextChunkOffset > riffEnd) {
                return null;
            }
            int dataOffset = chunkOffset + 8;
            int dataLength = (int) chunkLength;
            if (isChunk(content, chunkOffset, 'V', 'P', '8', 'X')) {
                extendedDimensions = webpExtendedDimensions(content, dataOffset, dataLength);
                if (extendedDimensions == null) {
                    return null;
                }
            } else if (isChunk(content, chunkOffset, 'V', 'P', '8', ' ')) {
                long[] dimensions = webpLossyDimensions(content, dataOffset, dataLength);
                if (dimensions == null) {
                    return null;
                }
                frameDimensions = dimensions;
                hasFramePayload = true;
            } else if (isChunk(content, chunkOffset, 'V', 'P', '8', 'L')) {
                long[] dimensions = webpLosslessDimensions(content, dataOffset, dataLength);
                if (dimensions == null) {
                    return null;
                }
                frameDimensions = dimensions;
                hasFramePayload = true;
            } else if (isChunk(content, chunkOffset, 'A', 'N', 'M', 'F')) {
                if (dataLength <= 16) {
                    return null;
                }
                hasFramePayload = true;
            }
            chunkOffset = (int) nextChunkOffset;
        }
        if (!hasFramePayload) {
            return null;
        }
        return extendedDimensions != null ? extendedDimensions : frameDimensions;
    }

    private static long[] webpLossyDimensions(byte[] content, int offset, int length) {
        if (length < 10
                || (content[offset] & 1) != 0
                || (content[offset + 3] & 0xff) != 0x9d
                || (content[offset + 4] & 0xff) != 0x01
                || (content[offset + 5] & 0xff) != 0x2a) {
            return null;
        }
        long frameTag = (content[offset] & 0xffL)
                | ((content[offset + 1] & 0xffL) << 8)
                | ((content[offset + 2] & 0xffL) << 16);
        long firstPartitionLength = frameTag >>> 5;
        if (length <= 10 || firstPartitionLength == 0 || firstPartitionLength > length - 3L) {
            return null;
        }
        long width = unsignedShortLittleEndian(content, offset + 6) & 0x3fffL;
        long height = unsignedShortLittleEndian(content, offset + 8) & 0x3fffL;
        return new long[]{width, height};
    }

    private static long[] webpLosslessDimensions(byte[] content, int offset, int length) {
        if (length <= 5 || (content[offset] & 0xff) != 0x2f) {
            return null;
        }
        long bits = unsignedIntLittleEndian(content, offset + 1);
        if ((bits >>> 29) != 0) {
            return null;
        }
        long width = (bits & 0x3fffL) + 1;
        long height = ((bits >>> 14) & 0x3fffL) + 1;
        return new long[]{width, height};
    }

    private static long[] webpExtendedDimensions(byte[] content, int offset, int length) {
        if (length < 10
                || (content[offset] & 0xc1) != 0
                || content[offset + 1] != 0
                || content[offset + 2] != 0
                || content[offset + 3] != 0) {
            return null;
        }
        long width = unsigned24LittleEndian(content, offset + 4) + 1;
        long height = unsigned24LittleEndian(content, offset + 7) + 1;
        return new long[]{width, height};
    }

    private static boolean isChunk(byte[] content, int offset, int a, int b, int c, int d) {
        return content[offset] == a && content[offset + 1] == b
                && content[offset + 2] == c && content[offset + 3] == d;
    }

    private static long unsignedShortLittleEndian(byte[] content, int offset) {
        return (content[offset] & 0xffL) | ((content[offset + 1] & 0xffL) << 8);
    }

    private static long unsigned24LittleEndian(byte[] content, int offset) {
        return (content[offset] & 0xffL)
                | ((content[offset + 1] & 0xffL) << 8)
                | ((content[offset + 2] & 0xffL) << 16);
    }

    private static long unsignedIntLittleEndian(byte[] content, int offset) {
        return (content[offset] & 0xffL)
                | ((content[offset + 1] & 0xffL) << 8)
                | ((content[offset + 2] & 0xffL) << 16)
                | ((content[offset + 3] & 0xffL) << 24);
    }

    private static boolean decodes(byte[] content) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            return image != null;
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean isPng(byte[] content) {
        return content.length >= 8
                && (content[0] & 0xff) == 0x89 && content[1] == 0x50 && content[2] == 0x4e
                && content[3] == 0x47 && content[4] == 0x0d && content[5] == 0x0a
                && (content[6] & 0xff) == 0x1a && content[7] == 0x0a;
    }

    private static boolean isJpeg(byte[] content) {
        return content.length >= 3
                && (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff;
    }

    private static boolean isGif(byte[] content) {
        return content.length >= 6
                && content[0] == 'G' && content[1] == 'I' && content[2] == 'F'
                && content[3] == '8' && (content[4] == '7' || content[4] == '9') && content[5] == 'a';
    }

    private static boolean isWebp(byte[] content) {
        return content.length >= 12
                && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
    }
}
