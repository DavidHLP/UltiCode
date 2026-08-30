package com.ulticode.modules.submission.port.adapter;

import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.UserExistencePort;
import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.service.SubmissionIntakePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("App Submission-owner intake adapter")
class RemoteSubmissionWritePortTest {

    @Mock private ProblemFactsPort problemFacts;
    @Mock private UserExistencePort userExistencePort;
    @Mock private SubmissionIntakePort owner;

    private RemoteSubmissionWritePort adapter;

    @BeforeEach
    void setUp() {
        adapter = new RemoteSubmissionWritePort(problemFacts, userExistencePort);
        ReflectionTestUtils.setField(adapter, "submissionIntake", owner);
    }

    @Test
    void capturesRequestOwnerFactsBeforeOrdinaryIntake() {
        CreateSubmissionDTO request = request();
        SubmissionVO expected = new SubmissionVO();
        when(problemFacts.findDisplayFacts(101L))
                .thenReturn(new ProblemFactsPort.ProblemDisplayFacts(101L, "Two Sum", "two-sum"));
        when(problemFacts.findLimits(101L))
                .thenReturn(new ProblemFactsPort.ProblemLimits(2, 256));
        when(problemFacts.findStarterCode(101L, "python")).thenReturn("print(0)");
        when(userExistencePort.existsById("user-1")).thenReturn(true);
        when(owner.submit(org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.same(request), org.mockito.ArgumentMatchers.any()))
                .thenReturn(expected);

        assertThat(adapter.submit("user-1", request)).isSameAs(expected);

        ArgumentCaptor<SubmissionFactsSnapshot> snapshot =
                ArgumentCaptor.forClass(SubmissionFactsSnapshot.class);
        verify(owner).submit(org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.same(request), snapshot.capture());
        assertThat(snapshot.getValue().admits("user-1", 101L)).isTrue();
        assertThat(snapshot.getValue().problem().timeLimitSeconds()).isEqualTo(2);
        assertThat(snapshot.getValue().problem().memoryLimitMb()).isEqualTo(256);
        assertThat(snapshot.getValue().problem().starterCode()).isEqualTo("print(0)");
    }

    @Test
    void sendsContestIntakeThroughTheExplicitOwnerCommand() {
        CreateSubmissionDTO request = request();
        request.setContestId("contest-1");
        when(problemFacts.findDisplayFacts(101L))
                .thenReturn(new ProblemFactsPort.ProblemDisplayFacts(101L, "Two Sum", "two-sum"));
        when(userExistencePort.existsById("user-1")).thenReturn(true);

        adapter.submitContest("user-1", request);

        verify(owner).submitContest(org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.same(request), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ownerFailurePropagatesWithoutLocalFallback() {
        CreateSubmissionDTO request = request();
        when(problemFacts.findDisplayFacts(101L))
                .thenReturn(new ProblemFactsPort.ProblemDisplayFacts(101L, "Two Sum", "two-sum"));
        when(userExistencePort.existsById("user-1")).thenReturn(true);
        when(owner.submit(org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.same(request), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("owner unavailable"));

        assertThatThrownBy(() -> adapter.submit("user-1", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("owner unavailable");
    }

    private static CreateSubmissionDTO request() {
        CreateSubmissionDTO request = new CreateSubmissionDTO();
        request.setProblemId(101L);
        request.setLanguage("python");
        request.setCode("print(1)");
        return request;
    }
}
