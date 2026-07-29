package com.ulticode.modules.submission.service;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.port.SubmissionWritePort;
import com.ulticode.modules.submission.service.impl.SubmissionAdministrationDomainServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionAdministrationDomainServiceTest {

    @Mock
    private SubmissionWritePort writePort;

    private Clock fixedClock;
    private SubmissionAdministrationDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneId.of("UTC"));
        service = new SubmissionAdministrationDomainServiceImpl(writePort, fixedClock);
    }

    @Nested
    @DisplayName("rejudge")
    class Rejudge {

        @Test
        @DisplayName("successful rejudge resets status to PENDING and updates entity")
        void success() {
            Submission submission = new Submission();
            submission.setId("sub-1");
            submission.setStatus("ACCEPTED");

            when(writePort.selectById("sub-1")).thenReturn(submission);

            Submission rejudged = service.rejudge("sub-1", true, "admin-1");

            assertThat(rejudged.getStatus()).isEqualTo("PENDING");
            verify(writePort).updateById(submission);
        }

        @Test
        @DisplayName("missing submission throws NOT_FOUND BusinessException")
        void notFound() {
            when(writePort.selectById("missing")).thenReturn(null);

            assertThatThrownBy(() -> service.rejudge("missing", false, "admin-1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(BaseErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("batchRejudge")
    class BatchRejudge {

        @Test
        @DisplayName("batchRejudge updates existing submissions")
        void success() {
            Submission sub1 = new Submission();
            sub1.setId("sub-1");
            Submission sub2 = new Submission();
            sub2.setId("sub-2");

            when(writePort.selectById("sub-1")).thenReturn(sub1);
            when(writePort.selectById("sub-2")).thenReturn(sub2);

            List<Submission> rejudged = service.batchRejudge(List.of("sub-1", "sub-2"), true, "admin-1");

            assertThat(rejudged).hasSize(2);
            verify(writePort, times(2)).updateById(any());
        }
    }
}
