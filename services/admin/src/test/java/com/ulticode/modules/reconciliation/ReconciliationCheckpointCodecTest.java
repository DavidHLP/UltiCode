package com.ulticode.modules.reconciliation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconciliationCheckpointCodecTest {

    private static final LocalDateTime WATERMARK = LocalDateTime.of(2026, 8, 29, 0, 0);

    private final ReconciliationCheckpointCodec codec =
            new ReconciliationCheckpointCodec(new ObjectMapper());

    @Test
    void encodePreservesTheContinuationWireShape() {
        ReconciliationCheckpointCodec.Progress progress = codec.initial(WATERMARK);
        progress.advanceSubmission(new OrphanScan.KeysetResult(2, "user-2", false));
        progress.advanceNotification(new OrphanScan.KeysetResult(3, "user-3", false));
        progress.advanceAudit(new OrphanScan.OffsetResult(4, 500, false));

        assertThat(codec.encode(progress)).isEqualTo(
                "{\"createdSince\":\"2026-08-29T00:00\","
                        + "\"submission\":{\"cursor\":\"user-2\",\"missing\":2,\"complete\":false},"
                        + "\"notification\":{\"cursor\":\"user-3\",\"missing\":3,\"complete\":false},"
                        + "\"audit\":{\"offset\":500,\"missing\":4,\"complete\":false}}");
    }

    @Test
    void decodeRestoresProgressFromThePersistedDetail() {
        String detail = detail("INCREMENTAL", WATERMARK.toString(),
                "user-15999", 7, false, "", 0, true, 16000, 2, false);

        ReconciliationCheckpointCodec.Progress progress =
                codec.decode("INCREMENTAL", WATERMARK, detail);

        assertThat(progress.submissionCursor()).isEqualTo("user-15999");
        assertThat(progress.submissionMissing()).isEqualTo(7);
        assertThat(progress.submissionComplete()).isFalse();
        assertThat(progress.notificationCursor()).isEmpty();
        assertThat(progress.notificationComplete()).isTrue();
        assertThat(progress.auditOffset()).isEqualTo(16000);
        assertThat(progress.auditMissing()).isEqualTo(2);
        assertThat(progress.auditComplete()).isFalse();
        assertThat(progress.isComplete()).isFalse();
    }

    @Test
    void resumeReadsLegacyDetailWithoutRunCheckpointMetadata() {
        ReconciliationRun partial = run("legacy-partial", LocalDateTime.of(2026, 8, 30, 0, 0),
                detail("INCREMENTAL", WATERMARK.toString(),
                        "user-15999", 7, false, "", 0, true, 0, 0, true));

        ReconciliationCheckpointCodec.Progress progress =
                codec.resume("INCREMENTAL", WATERMARK, partial, null);

        assertThat(progress.submissionCursor()).isEqualTo("user-15999");
        assertThat(progress.submissionMissing()).isEqualTo(7);
    }

    @Test
    void missingPartialStartsFromTheBeginning() {
        ReconciliationCheckpointCodec.Progress progress =
                codec.resume("FULL", null, null, null);

        assertThat(progress.submissionCursor()).isEmpty();
        assertThat(progress.notificationCursor()).isEmpty();
        assertThat(progress.auditOffset()).isZero();
    }

    @Test
    void watermarkMismatchStartsFromTheBeginning() {
        ReconciliationRun partial = run("partial", LocalDateTime.of(2026, 8, 30, 0, 0),
                detail("INCREMENTAL", WATERMARK.plusDays(1).toString(),
                        "user-15999", 7, false, "", 0, true, 0, 0, true));

        ReconciliationCheckpointCodec.Progress progress =
                codec.resume("INCREMENTAL", WATERMARK, partial, null);

        assertThat(progress.submissionCursor()).isEmpty();
        assertThat(progress.submissionMissing()).isZero();
        assertThat(progress.isComplete()).isFalse();
    }

    @Test
    void completedRunSupersedesEqualTimePartialByRunId() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 30, 0, 0);
        ReconciliationRun partial = run("run-1", startedAt,
                detail("FULL", null, "user-15999", 7, false, "", 0, true, 0, 0, true));
        ReconciliationRun completed = run("run-2", startedAt, null);

        ReconciliationCheckpointCodec.Progress progress =
                codec.resume("FULL", null, partial, completed);

        assertThat(progress.submissionCursor()).isEmpty();
        assertThat(progress.submissionMissing()).isZero();
    }

    @Test
    void invalidCheckpointFailsClosed() {
        String detail = detail("FULL", null, "user-1", 0, false, "", 0, true, 0, 0, true)
                .replace("\"missing\":0", "\"missing\":-0.5");

        assertThatThrownBy(() -> codec.decode("FULL", null, detail))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("invalid reconciliation checkpoint");
    }

    @Test
    void modeMismatchFailsClosed() {
        assertThatThrownBy(() -> codec.decode("FULL", null,
                detail("INCREMENTAL", null, "user-1", 0, false, "", 0, true, 0, 0, true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("invalid reconciliation checkpoint");
    }

    @Test
    void checkpointWithoutContinuationFailsClosed() {
        assertThatThrownBy(() -> codec.decode("FULL", null, "{\"mode\":\"FULL\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("invalid reconciliation checkpoint");
    }

    private static ReconciliationRun run(String runId, LocalDateTime startedAt, String detail) {
        ReconciliationRun run = new ReconciliationRun();
        run.setRunId(runId);
        run.setStartedAt(startedAt);
        run.setDetail(detail);
        return run;
    }

    private static String detail(String mode, String createdSince,
                                 String submissionCursor, long submissionMissing,
                                 boolean submissionComplete, String notificationCursor,
                                 long notificationMissing, boolean notificationComplete,
                                 int auditOffset, long auditMissing, boolean auditComplete) {
        String createdSinceJson = createdSince == null ? "null" : "\"" + createdSince + "\"";
        return "{\"mode\":\"" + mode + "\",\"continuation\":{"
                + "\"createdSince\":" + createdSinceJson
                + ",\"submission\":{\"cursor\":\"" + submissionCursor
                + "\",\"missing\":" + submissionMissing
                + ",\"complete\":" + submissionComplete + "}"
                + ",\"notification\":{\"cursor\":\"" + notificationCursor
                + "\",\"missing\":" + notificationMissing
                + ",\"complete\":" + notificationComplete + "}"
                + ",\"audit\":{\"offset\":" + auditOffset
                + ",\"missing\":" + auditMissing
                + ",\"complete\":" + auditComplete + "}}}";
    }
}
