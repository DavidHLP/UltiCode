package com.ulticode.app.api.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionLifecycleEventContractTest {

    @Test
    void freezesSubmissionOwnerAndEventShapes() {
        assertThat(SubmissionLifecycleEventContract.SCHEMA_VERSION).isEqualTo(1);
        assertThat(SubmissionLifecycleEventContract.OWNER).isEqualTo("Submission");
        assertThat(SubmissionLifecycleEventContract.ENVELOPE_FIELDS)
                .isEqualTo(IntegrationEventEnvelopeContract.FIELDS);
        assertThat(SubmissionLifecycleEventContract.ENVELOPE_FIELDS)
                .containsExactlyInAnyOrder(
                        "eventId", "owner", "eventType", "schemaVersion", "aggregateId",
                        "aggregateVersion", "causationId", "traceId", "payload");
        assertThat(SubmissionLifecycleEventContract.CREATED_EVENT_TYPE)
                .isEqualTo("SubmissionCreated");
        assertThat(SubmissionLifecycleEventContract.JUDGED_EVENT_TYPE)
                .isEqualTo("SubmissionJudged");
        assertThat(SubmissionLifecycleEventContract.CREATED_FIELDS)
                .containsExactlyInAnyOrder(
                        "eventId", "submissionId", "userId", "problemId", "contestId",
                        "generation", "language", "occurredAt");
        assertThat(SubmissionLifecycleEventContract.JUDGED_FIELDS)
                .containsExactlyInAnyOrder(
                        "eventId", "submissionId", "userId", "problemId", "contestId",
                        "generation", "attemptId", "status", "verdict", "runtimeMs",
                        "memoryMb", "occurredAt");
    }

    @Test
    void forbidsSensitiveSubmissionPayloadFields() {
        assertThat(SubmissionLifecycleEventContract.FORBIDDEN_FIELDS)
                .contains("code", "hiddenTestCases", "accessToken", "refreshToken", "password")
                .doesNotContain("language", "verdict");
    }
}
