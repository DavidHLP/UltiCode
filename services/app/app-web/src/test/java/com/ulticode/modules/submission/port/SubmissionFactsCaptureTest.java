package com.ulticode.modules.submission.port;

import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.UserExistencePort;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionFactsCaptureTest {

    @Mock private ProblemFactsPort problemFacts;
    @Mock private UserExistencePort userExistencePort;

    private SubmissionFactsCapture capture;

    @BeforeEach
    void setUp() {
        capture = new SubmissionFactsCapture(problemFacts, userExistencePort);
    }

    @Test
    void capturesTheExistingAdmissionReadSetAndSnapshotVersion() {
        CreateSubmissionDTO request = request();
        when(problemFacts.findDisplayFacts(101L))
                .thenReturn(new ProblemFactsPort.ProblemDisplayFacts(101L, "Two Sum", "two-sum"));
        when(problemFacts.findLimits(101L))
                .thenReturn(new ProblemFactsPort.ProblemLimits(2, 256));
        when(problemFacts.findStarterCode(101L, "python")).thenReturn("print(0)");
        when(userExistencePort.existsById("user-1")).thenReturn(true);

        SubmissionFactsSnapshot snapshot = capture.capture("user-1", request);

        assertThat(snapshot.admits("user-1", 101L)).isTrue();
        assertThat(snapshot.schemaVersion()).isEqualTo(SubmissionFactsSnapshot.CURRENT_SCHEMA_VERSION);
        assertThat(snapshot.problem().timeLimitSeconds()).isEqualTo(2);
        assertThat(snapshot.problem().memoryLimitMb()).isEqualTo(256);
        assertThat(snapshot.problem().starterCode()).isEqualTo("print(0)");
        verify(problemFacts).findDisplayFacts(101L);
        verify(problemFacts).findLimits(101L);
        verify(problemFacts).findStarterCode(101L, "python");
        verify(userExistencePort).existsById("user-1");
    }

    @Test
    void rejectsInvalidInputWithTypedBadRequestInsteadOfNull() {
        CreateSubmissionDTO request = request();
        request.setCode(" ");

        assertThatThrownBy(() -> capture.capture("user-1", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", BaseErrorCode.BAD_REQUEST)
                .hasMessage("Code cannot be empty");
        verifyNoInteractions(problemFacts, userExistencePort);
    }

    private static CreateSubmissionDTO request() {
        CreateSubmissionDTO request = new CreateSubmissionDTO();
        request.setProblemId(101L);
        request.setLanguage("python");
        request.setCode("print(1)");
        return request;
    }
}
