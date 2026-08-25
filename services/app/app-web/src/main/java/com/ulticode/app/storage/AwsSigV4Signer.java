package com.ulticode.app.storage;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Minimal AWS Signature Version 4 request signer (header-based, query-free).
 *
 * <p>Hand-rolled so the S3-compatible storage backend needs no additional
 * dependency; only object-level PUT/GET/DELETE against a path-style URL is
 * supported, which is all {@link S3Storage} requires.
 */
final class AwsSigV4Signer {

    static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    static final DateTimeFormatter DATE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private AwsSigV4Signer() {
    }

    /**
     * Signs the request described by {@code method}, {@code uri} and
     * {@code headers} (which must contain {@code host},
     * {@code x-amz-content-sha256} and {@code x-amz-date}) and returns the
     * {@code Authorization} header value.
     */
    static String authorization(String method, URI uri, Map<String, String> headers,
                                String payloadHash, String accessKey, String secretKey,
                                String region, String service, ZonedDateTime now) {
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);

        StringBuilder canonicalHeaders = new StringBuilder();
        StringBuilder signedHeaderNames = new StringBuilder();
        Map<String, String> lowerCased = new TreeMap<>();
        headers.forEach((name, value) -> lowerCased.put(name.toLowerCase(), value.trim()));
        for (Map.Entry<String, String> entry : lowerCased.entrySet()) {
            canonicalHeaders.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
        }
        signedHeaderNames.append(String.join(";", lowerCased.keySet()));

        String canonicalUri = uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        String canonicalRequest = method + '\n'
                + canonicalUri + '\n'
                + '\n' // no query string
                + canonicalHeaders + '\n'
                + signedHeaderNames + '\n'
                + payloadHash;

        String credentialScope = dateStamp + '/' + region + '/' + service + "/aws4_request";
        String stringToSign = ALGORITHM + '\n'
                + amzDate + '\n'
                + credentialScope + '\n'
                + hex(sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8)));

        byte[] signingKey = hmacSha256(
                hmacSha256(
                        hmacSha256(
                                hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp),
                                region),
                        service),
                "aws4_request");

        String signature = hex(hmacSha256(signingKey, stringToSign));
        return ALGORITHM + " Credential=" + accessKey + '/' + credentialScope
                + ", SignedHeaders=" + signedHeaderNames
                + ", Signature=" + signature;
    }

    /** RFC 3986 percent-encodes every path segment of a storage key. */
    static String encodeKeyPath(String key) {
        StringBuilder out = new StringBuilder();
        for (String segment : key.split("/", -1)) {
            if (!out.isEmpty()) {
                out.append('/');
            }
            out.append(encodeSegment(segment));
        }
        return out.toString();
    }

    private static String encodeSegment(String segment) {
        StringBuilder out = new StringBuilder();
        byte[] bytes = segment.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            char c = (char) (b & 0xff);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '.' || c == '_' || c == '~') {
                out.append(c);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit((c >> 4) & 0xf, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
            }
        }
        return out.toString();
    }

    static String sha256Hex(byte[] content) {
        return hex(sha256(content));
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(HEX[(b >> 4) & 0xf]).append(HEX[b & 0xf]);
        }
        return out.toString();
    }
}
