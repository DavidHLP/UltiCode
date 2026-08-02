package com.ulticode.modules.submission.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.submission.entity.Submission;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Serialization bridge for {@link Submission.TestCaseDetail} lists.
 *
 * <p>The app-api {@code SubmissionWritePort} contract uses {@code String testDetailsJson}
 * to avoid coupling the contract module to entity types. This codec provides the
 * bidirectional conversion between {@code List<TestCaseDetail>} and JSON string
 * so that both the consumer (backend-legacy, which has entity access) and the
 * implementation (backend-app, which also has entity access) can translate at
 * their respective boundaries.
 *
 * <p>Null or empty lists serialize to {@code null}; a null JSON string
 * deserializes to {@code null}.
 *
 * @author ulticode
 */
@Slf4j
public final class TestCaseDetailCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Submission.TestCaseDetail>> TYPE_REF = new TypeReference<>() {};

    private TestCaseDetailCodec() {
    }

    /**
     * Serialize a list of test-case details to a JSON string.
     *
     * @param testDetails the list to serialize (may be null or empty)
     * @return the JSON string, or {@code null} if the input is null or empty
     */
    public static String toJson(List<Submission.TestCaseDetail> testDetails) {
        if (testDetails == null || testDetails.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(testDetails);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize test details: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Deserialize a JSON string back to a list of test-case details.
     *
     * @param json the JSON string (may be null or blank)
     * @return the deserialized list, or {@code null} if the input is null or blank
     */
    public static List<Submission.TestCaseDetail> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, TYPE_REF);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize test details: {}", e.getMessage());
            return null;
        }
    }
}
