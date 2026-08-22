package com.ulticode.submission.port;

import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.UserExistencePort;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import com.ulticode.modules.submission.port.DefaultSubmissionWritePort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmissionFactsSnapshotTest {

    @Test
    void admitsOnlyTheCapturedUserAndProblem() {
        SubmissionFactsSnapshot snapshot = new SubmissionFactsSnapshot(
                "user-1", true,
                new SubmissionFactsSnapshot.ProblemFacts(
                        101L, "Two Sum", "two-sum", 2, 256, ""),
                100L, SubmissionFactsSnapshot.CURRENT_SCHEMA_VERSION);

        assertThat(snapshot.admits("user-1", 101L)).isTrue();
        assertThat(snapshot.admits("user-2", 101L)).isFalse();
        assertThat(snapshot.admits("user-1", 102L)).isFalse();
    }

    @Test
    void missingFactsCannotAdmitAWrite() {
        SubmissionFactsSnapshot snapshot = new SubmissionFactsSnapshot(
                "user-1", false, null, 100L,
                SubmissionFactsSnapshot.CURRENT_SCHEMA_VERSION);

        assertThat(snapshot.admits("user-1", 101L)).isFalse();
    }

    @Test
    void ownerWriterDoesNotDependOnAppOrAuthFactsPorts() {
        assertThat(Arrays.stream(DefaultSubmissionWritePort.class.getDeclaredFields())
                .map(Field::getType))
                .doesNotContain(ProblemFactsPort.class, UserExistencePort.class);
    }

    @Test
    void rejectsMalformedMetadataAtTheContractBoundary() {
        assertThatThrownBy(() -> new SubmissionFactsSnapshot(
                "", true, null, 100L,
                SubmissionFactsSnapshot.CURRENT_SCHEMA_VERSION))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SubmissionFactsSnapshot(
                "user-1", true, null, -1L,
                SubmissionFactsSnapshot.CURRENT_SCHEMA_VERSION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsupportedSchemaVersionsAtTheContractBoundary() {
        SubmissionFactsSnapshot snapshot = new SubmissionFactsSnapshot(
                "user-1", true,
                new SubmissionFactsSnapshot.ProblemFacts(
                        101L, "Two Sum", "two-sum", 2, 256, ""),
                100L, SubmissionFactsSnapshot.CURRENT_SCHEMA_VERSION + 1);

        assertThat(snapshot.admits("user-1", 101L)).isFalse();
    }

    @Test
    void problemFactsMustMatchTheRequestedProblem() {
        SubmissionFactsSnapshot snapshot = new SubmissionFactsSnapshot(
                "user-1", true,
                new SubmissionFactsSnapshot.ProblemFacts(
                        101L, "Two Sum", "two-sum", 2, 256, ""),
                100L, SubmissionFactsSnapshot.CURRENT_SCHEMA_VERSION);

        assertThat(snapshot.admits("user-1", 102L)).isFalse();
    }

    @Test
    void contractShapeRemainsTheMinimalOwnerValidationSet() {
        assertThat(Arrays.stream(SubmissionFactsSnapshot.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList())
                .containsExactly("userId", "userExists", "problem", "capturedAtEpochMillis", "schemaVersion");
        assertThat(Arrays.stream(SubmissionFactsSnapshot.class.getRecordComponents())
                .map(RecordComponent::getType)
                .toList())
                .containsExactly(String.class, boolean.class,
                        SubmissionFactsSnapshot.ProblemFacts.class, long.class, int.class);

        assertThat(Arrays.stream(SubmissionFactsSnapshot.ProblemFacts.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList())
                .containsExactly("id", "title", "slug", "timeLimitSeconds", "memoryLimitMb", "starterCode");
        assertThat(Arrays.stream(SubmissionFactsSnapshot.ProblemFacts.class.getRecordComponents())
                .map(RecordComponent::getType)
                .toList())
                .containsExactly(Long.class, String.class, String.class,
                        Integer.class, Integer.class, String.class);
    }
}
