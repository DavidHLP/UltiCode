package com.ulticode.modules.submission.runtime.async;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** HTTP Adapter for Judge0; no vendor type leaves this package. */
@Component
@ConditionalOnExpression("'${judge.async.executor:docker}' == 'judge0' "
        + "&& '${judge0.enabled:false}' == 'true'")
final class Judge0HttpTransport implements Judge0Transport {

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final Judge0Properties properties;
    private final URI endpoint;

    Judge0HttpTransport(ObjectMapper objectMapper, Judge0Properties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        properties.validateEnabledConfiguration();
        this.endpoint = URI.create(trimTrailingSlash(properties.getEndpoint()));
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                .build();
    }

    @Override
    public String submit(Submission submission) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("language_id", submission.languageId());
        body.put("source_code", submission.sourceCode());
        body.put("stdin", submission.stdin());
        body.put("expected_output", submission.expectedOutput());
        body.put("cpu_time_limit", submission.timeoutMs() / 1000.0);
        body.put("memory_limit", submission.memoryLimitKb());
        body.put("max_file_size", Math.max(1, (submission.maxOutputBytes() + 1_023) / 1_024));
        JsonNode response = send("POST", "/submissions?base64_encoded=false&wait=false", body);
        return text(response, "token");
    }

    @Override
    public Poll poll(String token) {
        validateToken(token);
        JsonNode response = send("GET",
                "/submissions/" + token + "?base64_encoded=false", null);
        JsonNode statusNode = response.path("status");
        return new Poll(
                status(statusNode),
                nullableText(response, "stdout"),
                nullableText(response, "stderr"),
                nullableText(response, "compile_output"),
                nullableText(response, "message"),
                elapsedMs(response.path("time")),
                memoryBytes(response.path("memory")));
    }

    @Override
    public void cancel(String token) {
        validateToken(token);
        send("DELETE", "/submissions/" + token, null);
    }

    private JsonNode send(String method, String path, Map<String, Object> body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint.resolve(path))
                    .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                    .header(properties.getApiKeyHeader(), properties.getApiKey())
                    .header("Accept", "application/json");
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(body)));
            }
            HttpResponse<InputStream> response = client.send(request.build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (InputStream ignored = response.body()) {
                    throw new IllegalStateException("Judge0 request failed with HTTP "
                            + response.statusCode());
                }
            }
            int maxResponseBytes = Math.max(properties.getMaxOutputBytes() * 4, 65_536);
            try (InputStream bodyStream = response.body()) {
                byte[] bytes = bodyStream.readNBytes(maxResponseBytes + 1);
                if (bytes.length > maxResponseBytes) {
                    throw new IllegalStateException("Judge0 response exceeded the configured limit");
                }
                return objectMapper.readTree(bytes);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Judge0 request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Judge0 request interrupted", exception);
        }
    }

    static Judge0Transport.Status status(int id) {
        return switch (id) {
            case 1 -> Judge0Transport.Status.QUEUED;
            case 2 -> Judge0Transport.Status.RUNNING;
            case 3 -> Judge0Transport.Status.ACCEPTED;
            case 4 -> Judge0Transport.Status.WRONG_ANSWER;
            case 5 -> Judge0Transport.Status.TIME_LIMIT_EXCEEDED;
            case 6 -> Judge0Transport.Status.COMPILE_ERROR;
            case 7, 8, 9, 10, 11, 12 -> Judge0Transport.Status.RUNTIME_ERROR;
            case 13, 14 -> Judge0Transport.Status.FAILED;
            case 15 -> Judge0Transport.Status.MEMORY_LIMIT_EXCEEDED;
            case 17 -> Judge0Transport.Status.OUTPUT_LIMIT_EXCEEDED;
            default -> Judge0Transport.Status.FAILED;
        };
    }

    private static Judge0Transport.Status status(JsonNode status) {
        String description = status.path("description").asText("").toLowerCase(Locale.ROOT);
        if (description.contains("memory")) {
            return Judge0Transport.Status.MEMORY_LIMIT_EXCEEDED;
        }
        if (description.contains("output")) {
            return Judge0Transport.Status.OUTPUT_LIMIT_EXCEEDED;
        }
        if (description.contains("time limit")) {
            return Judge0Transport.Status.TIME_LIMIT_EXCEEDED;
        }
        return status(status.path("id").asInt(-1));
    }

    private static long elapsedMs(JsonNode value) {
        if (!value.isNumber() && !value.isTextual()) {
            return 0L;
        }
        try {
            return Math.max(0L, Math.round(value.asDouble(0.0) * 1000.0));
        } catch (ArithmeticException exception) {
            return 0L;
        }
    }

    private static long memoryBytes(JsonNode value) {
        if (!value.isNumber()) {
            return 0L;
        }
        try {
            return Math.max(0L, Math.multiplyExact(value.asLong(0L), 1024L));
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static String text(JsonNode object, String field) {
        String value = nullableText(object, field);
        if (value == null) {
            throw new IllegalStateException("Judge0 response is missing " + field);
        }
        return value;
    }

    private static String nullableText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static void validateToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{1,200}")) {
            throw new IllegalArgumentException("invalid Judge0 execution token");
        }
    }

    private static String trimTrailingSlash(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
