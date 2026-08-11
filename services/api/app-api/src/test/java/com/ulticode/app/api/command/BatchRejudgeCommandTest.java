package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchRejudgeCommandTest {

    @Test
    void rejectsBlankSubmissionIdsAndCopiesTheList() {
        assertThatThrownBy(() -> command(List.of("sub-1", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-blank");

        assertThatThrownBy(() -> command(List.of("sub-1", "sub-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicates");

        assertThatThrownBy(() -> new BatchRejudgeCommand(
                "cmd-1", IdMetadata.mint(),
                new ActorDelegation("ADMIN", "admin-1", "admin-1", "rejudge"),
                null, List.of("sub-1"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trace");

        List<String> ids = new ArrayList<>(List.of("sub-1"));
        BatchRejudgeCommand command = new BatchRejudgeCommand(
                "cmd-1", IdMetadata.mint(),
                new ActorDelegation("ADMIN", "admin-1", "admin-1", "rejudge"),
                new TraceMetadata("t-1", null, null, null), ids, false);
        ids.add("sub-2");

        assertThat(command.submissionIds()).containsExactly("sub-1");
    }

    private static BatchRejudgeCommand command(List<String> ids) {
        return new BatchRejudgeCommand(
                "cmd-1", IdMetadata.mint(),
                new ActorDelegation("ADMIN", "admin-1", "admin-1", "rejudge"),
                new TraceMetadata("t-1", null, null, null), ids, false);
    }
}
