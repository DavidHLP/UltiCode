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
    @DisplayName("accepts the RIFF/WEBP container without an ImageIO decode")
    void detectsWebpContainer() {
        byte[] webp = new byte[]{
                'R', 'I', 'F', 'F', 0x1a, 0x00, 0x00, 0x00,
                'W', 'E', 'B', 'P', 'V', 'P', '8', ' ',
                0x0e, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };

        ImageContent.Detected detected = ImageContent.detect(webp);

        assertThat(detected).isNotNull();
        assertThat(detected.extension()).isEqualTo("webp");
        assertThat(detected.contentType()).isEqualTo("image/webp");
    }
}
