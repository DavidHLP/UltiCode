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
    private static final byte[] ONE_PIXEL_WEBP = Base64.getDecoder().decode(
            "UklGRhwAAABXRUJQVlA4TA8AAAAvAAAAAAcQ/Y/+ByKi/wEA");
    private static final byte[] TWO_BY_THREE_WEBP = Base64.getDecoder().decode(
            "UklGRhwAAABXRUJQVlA4TA8AAAAvAYAAAAcQ/Y/+ByKi/wEA");
    private static final byte[] ANIMATED_GIF = {
            'G', 'I', 'F', '8', '9', 'a',
            1, 0, 1, 0, (byte) 0x80, 0, 0,
            0, 0, 0, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            0x21, (byte) 0xf9, 4, 0, 0, 0, 0, 0,
            0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0,
            2, 2, 0x44, 0x01, 0,
            0x21, (byte) 0xf9, 4, 0, 0, 0, 0, 0,
            0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0,
            2, 2, 0x4c, 0x01, 0,
            0x3b
    };
    private static final byte[] STATIC_GIF = Base64.getDecoder().decode(
            "R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==");

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

    private static byte[] vp8LossyFrame(int width, int height) {
        byte[] header = vp8LossyHeader(width, height);
        header[0] = 0x20; // key frame with a one-byte first partition
        return java.util.Arrays.copyOf(header, header.length + 1);
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
    @DisplayName("rejects an animated PNG whose frames the JDK reader never validates")
    void rejectsAnimatedPng() {
        assertThatThrownBy(() -> ImageContent.assertWithinPixelBudget(withAnimationChunks(ONE_PIXEL_PNG)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageContent.detect(withAnimationChunks(ONE_PIXEL_PNG)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Splices {@code acTL}/{@code fdAT} right after the IHDR of a decodable PNG. */
    private static byte[] withAnimationChunks(byte[] png) {
        int ihdrEnd = 8 + 4 + 4 + 13 + 4;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(java.util.Arrays.copyOfRange(png, 0, ihdrEnd));
        out.writeBytes(chunk("acTL", new byte[]{0, 0, 0, 2, 0, 0, 0, 0}));
        out.writeBytes(chunk("fdAT", new byte[]{0, 0, 0, 1, 0, 0, 0, 0}));
        out.writeBytes(java.util.Arrays.copyOfRange(png, ihdrEnd, png.length));
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
    @DisplayName("rejects animated GIFs before decoding any frame")
    void rejectsAnimatedGif() {
        assertThatThrownBy(() -> ImageContent.assertWithinPixelBudget(ANIMATED_GIF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageContent.detect(ANIMATED_GIF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("accepts a static GIF after checking its frame count")
    void acceptsStaticGif() {
        ImageContent.Detected detected = ImageContent.detect(STATIC_GIF);

        assertThat(detected).isNotNull();
        assertThat(detected.extension()).isEqualTo("gif");
        assertThat(detected.contentType()).isEqualTo("image/gif");
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
    @DisplayName("detects a real WebP with a complete frame")
    void detectsWebpContainer() {
        byte[] image = ONE_PIXEL_WEBP;

        ImageContent.Detected detected = ImageContent.detect(image);

        assertThat(detected).isNotNull();
        assertThat(detected.extension()).isEqualTo("webp");
        assertThat(detected.contentType()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("rejects a WebP frame header without compressed payload")
    void rejectsWebpWithoutFramePayload() {
        byte[] headerOnly = webp(webpChunk("VP8 ", vp8LossyHeader(1, 1)));

        assertThatThrownBy(() -> ImageContent.detect(headerOnly))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dimensions");
    }
    @Test
    @DisplayName("rejects a VP8L header with one trailing byte")
    void rejectsShortVp8lPayload() {
        byte[] malformed = webp(webpChunk("VP8L",
                java.util.Arrays.copyOf(vp8LosslessHeader(1, 1), 6)));

        assertThat(malformed).hasSize(26);
        assertThat(ImageContent.detect(malformed)).isNull();
    }

    @Test
    @DisplayName("reads VP8L dimensions and rejects animated WebP")
    void readsWebpVariants() {
        ImageContent.assertWithinPixelBudget(TWO_BY_THREE_WEBP);
        ImageContent.assertWithinPixelBudget(webp(
                webpChunk("VP8X", vp8ExtendedHeader(4, 5)),
                webpChunk("VP8 ", vp8LossyFrame(4, 5))));
        byte[] animatedHeader = vp8ExtendedHeader(4, 5);
        animatedHeader[0] |= 0x20;
        byte[] animated = webp(
                webpChunk("VP8X", animatedHeader),
                webpChunk("ANMF", new byte[17]));
        assertThatThrownBy(() -> ImageContent.detect(animated))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects WebP dimensions over either image bound")
    void rejectsOversizedWebp() {
        assertThatThrownBy(() -> ImageContent.assertWithinPixelBudget(
                webp(
                        webpChunk("VP8X", vp8ExtendedHeader(16_383, 1)),
                        webpChunk("VP8 ", vp8LossyFrame(16_383, 1)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4096x4096");
        assertThatThrownBy(() -> ImageContent.detect(
                webp(
                        webpChunk("VP8X", vp8ExtendedHeader(4_096, 4_097)),
                        webpChunk("VP8 ", vp8LossyFrame(4_096, 4_097)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4096x4096");
    }

    @Test
    @DisplayName("rejects WebP files that stack conflicting or duplicate image headers")
    void rejectsConflictingWebpHeaders() {
        byte[] oversizedBeforeSmallFrame = webp(
                webpChunk("VP8L", java.util.Arrays.copyOf(vp8LosslessHeader(16_384, 1), 6)),
                webpChunk("VP8 ", vp8LossyFrame(2, 2)));
        assertThatThrownBy(() -> ImageContent.assertWithinPixelBudget(oversizedBeforeSmallFrame))
                .isInstanceOf(IllegalArgumentException.class);

        byte[] oversizedFrameOnSmallCanvas = webp(
                webpChunk("VP8X", vp8ExtendedHeader(2, 2)),
                webpChunk("VP8 ", vp8LossyFrame(16_383, 1)));
        assertThatThrownBy(() -> ImageContent.assertWithinPixelBudget(oversizedFrameOnSmallCanvas))
                .isInstanceOf(IllegalArgumentException.class);

        byte[] duplicateFrames = webp(
                webpChunk("VP8 ", vp8LossyFrame(2, 2)),
                webpChunk("VP8 ", vp8LossyFrame(3, 3)));
        assertThatThrownBy(() -> ImageContent.assertWithinPixelBudget(duplicateFrames))
                .isInstanceOf(IllegalArgumentException.class);

        byte[] duplicateCanvases = webp(
                webpChunk("VP8X", vp8ExtendedHeader(2, 2)),
                webpChunk("VP8X", vp8ExtendedHeader(3, 3)),
                webpChunk("VP8 ", vp8LossyFrame(3, 3)));
        assertThatThrownBy(() -> ImageContent.assertWithinPixelBudget(duplicateCanvases))
                .isInstanceOf(IllegalArgumentException.class);
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
