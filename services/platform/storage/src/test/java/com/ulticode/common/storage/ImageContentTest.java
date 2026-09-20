package com.ulticode.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Shared image sniffing and decode bounds.
 *
 * <p>Both avatar paths must reject an image whose decoded raster would exceed the pixel budget *before* decoding it,
 * which is why the dimension check reads header metadata only.
 */
@DisplayName("ImageContent")
class ImageContentTest {

    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    /** Builds a PNG signature + IHDR with the given dimensions (CRC-correct, no pixel data). */
    private static byte[] pngHeader(int width, int height) {
        ByteBuffer ihdr = ByteBuffer.allocate(13);
        ihdr.putInt(width);
        ihdr.putInt(height);
        ihdr.put((byte) 8);   // bit depth
        ihdr.put((byte) 2);   // colour type: truecolour
        ihdr.put((byte) 0);   // compression
        ihdr.put((byte) 0);   // filter
        ihdr.put((byte) 0);   // interlace
        byte[] ihdrData = ihdr.array();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a});
        out.writeBytes(chunk("IHDR", ihdrData));
        return out.toByteArray();
    }

    private static byte[] chunk(String type, byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(type.getBytes(StandardCharsets.US_ASCII));
        crc.update(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(type.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        return out.toByteArray();
    }

    private static byte[] webp(byte[]... chunks) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(new byte[]{'W', 'E', 'B', 'P'});
        for (byte[] chunk : chunks) {
            body.writeBytes(chunk);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{'R', 'I', 'F', 'F'});
        out.writeBytes(littleEndianInt(body.size()));
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }

    private static byte[] webpChunk(String type, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(type.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(littleEndianInt(data.length));
        out.writeBytes(data);
        if ((data.length & 1) != 0) {
            out.write(0);
        }
        return out.toByteArray();
    }

    private static byte[] littleEndianInt(int value) {
        return new byte[]{
                (byte) value,
                (byte) (value >>> 8),
                (byte) (value >>> 16),
                (byte) (value >>> 24)
        };
    }

    private static byte[] vp8LossyHeader(int width, int height) {
        return new byte[]{
                0, 0, 0, (byte) 0x9d, 0x01, 0x2a,
                (byte) width, (byte) (width >>> 8),
                (byte) height, (byte) (height >>> 8)
        };
    }

    private static byte[] vp8LosslessHeader(int width, int height) {
        long bits = (width - 1L) | ((height - 1L) << 14);
        return new byte[]{
                0x2f,
                (byte) bits, (byte) (bits >>> 8), (byte) (bits >>> 16), (byte) (bits >>> 24)
        };
    }

    private static byte[] vp8ExtendedHeader(int width, int height) {
        long widthMinusOne = width - 1L;
        long heightMinusOne = height - 1L;
        return new byte[]{
                0x02, 0, 0, 0,
                (byte) widthMinusOne, (byte) (widthMinusOne >>> 8), (byte) (widthMinusOne >>> 16),
                (byte) heightMinusOne, (byte) (heightMinusOne >>> 8), (byte) (heightMinusOne >>> 16)
        };
    }

    @Test
    @DisplayName("detects a real PNG inside the pixel budget")
    void detectsPng() {
        ImageContent.Detected detected = ImageContent.detect(ONE_PIXEL_PNG);

        assertThat(detected).isNotNull();
        assertThat(detected.extension()).isEqualTo("png");
        assertThat(detected.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("rejects an oversized raster from header metadata before decoding")
    void rejectsOversizedRasterWithoutDecoding() {
        byte[] bomb = pngHeader(50_000, 50_000);

        assertThatThrownBy(() -> ImageContent.assertWithinPixelBudget(bomb))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4096x4096");
        // Detection fails loudly too: callers map this to their own BAD_REQUEST contract.
        assertThatThrownBy(() -> ImageContent.detect(bomb))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4096x4096");
    }

    @Test
    @DisplayName("accepts a raster exactly at the budget and rejects one pixel over")
    void budgetBoundary() {
        ImageContent.assertWithinPixelBudget(pngHeader(4096, 4096));

        assertThatThrownBy(() -> ImageContent.assertWithinPixelBudget(pngHeader(4096, 4097)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4096x4096");
    }

    @Test
    @DisplayName("treats unreadable dimensions and non-images as unsupported")
    void unreadableInputIsRejected() {
        assertThatThrownBy(() -> ImageContent.assertWithinPixelBudget("not an image".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(ImageContent.detect("not an image".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(ImageContent.detect(new byte[0])).isNull();
        assertThat(ImageContent.detect(null)).isNull();
    }

    @Test
    @DisplayName("detects a valid VP8 WebP without an ImageIO decode")
    void detectsWebpContainer() {
        byte[] image = webp(webpChunk("VP8 ", vp8LossyHeader(1, 1)));

        ImageContent.Detected detected = ImageContent.detect(image);

        assertThat(detected).isNotNull();
        assertThat(detected.extension()).isEqualTo("webp");
        assertThat(detected.contentType()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("reads VP8L and animated VP8X dimensions")
    void readsWebpVariants() {
        ImageContent.assertWithinPixelBudget(webp(webpChunk("VP8L", vp8LosslessHeader(2, 3))));
        ImageContent.assertWithinPixelBudget(webp(webpChunk("VP8X", vp8ExtendedHeader(4, 5))));
    }

    @Test
    @DisplayName("rejects WebP dimensions over either image bound")
    void rejectsOversizedWebp() {
        assertThatThrownBy(() -> ImageContent.assertWithinPixelBudget(
                webp(webpChunk("VP8X", vp8ExtendedHeader(16_384, 1)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4096x4096");
        assertThatThrownBy(() -> ImageContent.detect(
                webp(webpChunk("VP8X", vp8ExtendedHeader(4_096, 4_097)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4096x4096");
    }

    @Test
    @DisplayName("rejects truncated and unknown WebP chunks without indexing past the input")
    void rejectsMalformedWebp() {
        byte[] truncated = new byte[]{
                'R', 'I', 'F', 'F', 12, 0, 0, 0, 'W', 'E', 'B', 'P',
                'V', 'P', '8', ' ', 8, 0, 0, 0
        };
        assertThatThrownBy(() -> ImageContent.assertWithinPixelBudget(truncated))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ImageContent.assertWithinPixelBudget(
                webp(webpChunk("JUNK", new byte[0]))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
