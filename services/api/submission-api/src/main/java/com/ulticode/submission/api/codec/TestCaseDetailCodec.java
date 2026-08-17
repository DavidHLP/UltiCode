package com.ulticode.submission.api.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.submission.api.dto.SubmissionTestCaseDetailDTO;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Canonical codec for the persisted {@code submissions.test_details} JSON.
 *
 * <p>The codec deliberately accepts and returns the entity-free Submission API
 * DTO. Storage owners map that DTO to their local entity after crossing this
 * boundary; neither entity module is shared with the other owner.
 */
public final class TestCaseDetailCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<SubmissionTestCaseDetailDTO>> TYPE_REF =
            new TypeReference<>() {};
    private static final Logger LOGGER = Logger.getLogger(TestCaseDetailCodec.class.getName());

    private TestCaseDetailCodec() {
    }

    /** Serialize details using the legacy null-for-empty convention. */
    public static String toJson(List<SubmissionTestCaseDetailDTO> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            LOGGER.log(Level.WARNING, "Failed to serialize submission test details", exception);
            return null;
        }
    }

    /** Deserialize current and legacy rows, failing closed on malformed JSON. */
    public static List<SubmissionTestCaseDetailDTO> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, TYPE_REF);
        } catch (JsonProcessingException exception) {
            LOGGER.log(Level.WARNING, "Failed to deserialize submission test details", exception);
            return null;
        }
    }
}
