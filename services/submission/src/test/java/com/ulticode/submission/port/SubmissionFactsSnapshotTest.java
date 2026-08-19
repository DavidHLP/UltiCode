package com.ulticode.submission.port;

import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.UserExistencePort;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import com.ulticode.modules.submission.port.DefaultSubmissionWritePort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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
}
