package com.ulticode.modules.queue.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/** Serializes judge-owned details without loading the Submission entity. */
@Slf4j
public final class JudgeTestCaseDetailCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JudgeTestCaseDetailCodec() {
    }

    public static String toJson(List<JudgeTestCaseDetail> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize judge test details: {}", e.getMessage());
            return null;
        }
    }
}
