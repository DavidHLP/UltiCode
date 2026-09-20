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
            // WebP is not decodable by ImageIO out of the box; the RIFF/WEBP container header is the bound check.
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
        long pixels = dimensions[0] * dimensions[1];
        if (dimensions[0] <= 0 || dimensions[1] <= 0 || pixels > MAX_PIXELS) {
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
