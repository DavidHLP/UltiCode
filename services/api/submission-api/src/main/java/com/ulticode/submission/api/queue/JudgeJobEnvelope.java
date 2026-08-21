package com.ulticode.submission.api.queue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Versioned wire envelope for Submission-to-Judge dispatches.
 *
 * <p>Both the Submission producer and Judge consumer compile against this
 * provider-owned contract. Version 1 is the legacy unfenced shape; version 2
 * carries generation and attempt identity for fenced execution.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record JudgeJobEnvelope(
        @JsonProperty("version") int version,
        @JsonProperty("id") String id,
        @JsonProperty("submissionId") String submissionId,
        @JsonProperty("problemId") String problemId,
        @JsonProperty("userId") String userId,
        @JsonProperty("language") String language,
        @JsonProperty("code") String code,
        @JsonProperty("timeLimitMs") int timeLimitMs,
        @JsonProperty("memoryLimitKb") int memoryLimitKb,
        @JsonProperty("generation") Long generation,
        @JsonProperty("attemptId") String attemptId) {

    public static final int VERSION_1 = 1;
    public static final int VERSION_2 = 2;

    public boolean isFenceAware() {
        return version >= VERSION_2 && generation != null && attemptId != null;
    }
}
