package com.ulticode.submission.port.adapter;

import com.ulticode.modules.submission.created.SubmissionCreatedOutboxMapper;
import com.ulticode.modules.submission.created.SubmissionCreatedOutboxRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoopContestSubmissionPortTest {

    @Mock
    private SubmissionCreatedOutboxMapper createdOutboxMapper;

    @Test
    void exposesRemoteContestContextToVerdictWriters() {
        SubmissionCreatedOutboxRecord record = new SubmissionCreatedOutboxRecord();
        record.setContestId("contest-1");
        record.setVirtualSessionId("session-1");
        when(createdOutboxMapper.findLatestBySubmissionId("submission-1")).thenReturn(record);

        NoopContestSubmissionPort port = new NoopContestSubmissionPort(createdOutboxMapper);

        assertThat(port.findContestId("submission-1")).isEqualTo("contest-1");
        assertThat(port.isContestSubmission("submission-1")).isTrue();
        assertThat(port.isVirtualParticipation("submission-1")).isTrue();
    }
}
