package com.ulticode.submission.port.adapter;

import com.ulticode.modules.submission.created.SubmissionCreatedOutboxMapper;
import com.ulticode.modules.submission.created.SubmissionCreatedOutboxRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxContestSubmissionPortTest {

    @Mock
    private SubmissionCreatedOutboxMapper createdOutboxMapper;

    @Test
    void exposesRemoteContestContextToVerdictWriters() {
        SubmissionCreatedOutboxRecord record = new SubmissionCreatedOutboxRecord();
        record.setContestId("contest-1");
        record.setVirtualSessionId("session-1");
        when(createdOutboxMapper.findLatestBySubmissionId("submission-1")).thenReturn(record);

        OutboxContestSubmissionPort port = new OutboxContestSubmissionPort(createdOutboxMapper);

        assertThat(port.findContestId("submission-1")).isEqualTo("contest-1");
        assertThat(port.isContestSubmission("submission-1")).isTrue();
        assertThat(port.isVirtualParticipation("submission-1")).isTrue();
    }

    @Test
    void returnsNullWhenCreatedRowIsGenuinelyAbsent() {
        when(createdOutboxMapper.findLatestBySubmissionId("submission-2")).thenReturn(null);

        OutboxContestSubmissionPort port = new OutboxContestSubmissionPort(createdOutboxMapper);

        assertThat(port.findContestId("submission-2")).isNull();
        assertThat(port.isContestSubmission("submission-2")).isFalse();
        assertThat(port.isVirtualParticipation("submission-2")).isFalse();
    }

    @Test
    void propagatesReadFailureInsteadOfSwallowingIt() {
        when(createdOutboxMapper.findLatestBySubmissionId("submission-3"))
                .thenThrow(new IllegalStateException("connection lost"));

        OutboxContestSubmissionPort port = new OutboxContestSubmissionPort(createdOutboxMapper);

        // Fail-closed: verdict writers must roll back and retry rather than
        // emit a judged event without contest context.
        assertThatThrownBy(() -> port.findContestId("submission-3"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("connection lost");
    }
}
