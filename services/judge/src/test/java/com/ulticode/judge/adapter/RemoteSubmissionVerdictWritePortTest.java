package com.ulticode.judge.adapter;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.submission.api.service.SubmissionVerdictWritePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteSubmissionVerdictWritePortTest {

    private final SubmissionVerdictWritePort owner = mock(SubmissionVerdictWritePort.class);
    private final RemoteSubmissionVerdictWritePort adapter = new RemoteSubmissionVerdictWritePort();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adapter, "submissionVerdict", owner);
    }

    @Test
    void delegatesOnlyVerdictCapabilities() {
        when(owner.updateSubmissionResultFenced(
                "sub-1", SubmissionStatus.ACCEPTED, 12, 1.5, "[]", 3L, "attempt-1"))
                .thenReturn(true);

        adapter.updateSubmissionResult(
                "sub-1", SubmissionStatus.JUDGING, 0, 0.0, null);
        assertTrue(adapter.updateSubmissionResultFenced(
                "sub-1", SubmissionStatus.ACCEPTED, 12, 1.5, "[]", 3L, "attempt-1"));

        verify(owner).updateSubmissionResult(
                "sub-1", SubmissionStatus.JUDGING, 0, 0.0, null);
    }
}
