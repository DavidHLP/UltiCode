package com.ulticode.modules.submission.port.adapter;

import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.service.SubmissionIntakePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock private SubmissionIntakePort owner;

    private RemoteSubmissionWritePort adapter;

    @BeforeEach
    void setUp() {
        adapter = new RemoteSubmissionWritePort();
        ReflectionTestUtils.setField(adapter, "submissionIntake", owner);
    }

    @Test
    void forwardsOrdinaryIntakeWithCapturedFacts() {
        CreateSubmissionDTO request = request();
        SubmissionFactsSnapshot facts = facts();
        SubmissionVO expected = new SubmissionVO();
        when(owner.submit(org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.same(request), org.mockito.ArgumentMatchers.same(facts)))
                .thenReturn(expected);

        assertThat(adapter.submit("user-1", request, facts)).isSameAs(expected);

        verify(owner).submit(org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.same(request), org.mockito.ArgumentMatchers.same(facts));
    }

    @Test
    void sendsContestIntakeThroughTheExplicitOwnerCommand() {
        CreateSubmissionDTO request = request();
        request.setContestId("contest-1");
        SubmissionFactsSnapshot facts = facts();

        adapter.submitContest("user-1", request, facts);

        verify(owner).submitContest(org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.same(request), org.mockito.ArgumentMatchers.same(facts));
    }

    @Test
    void ownerFailurePropagatesWithoutLocalFallback() {
        CreateSubmissionDTO request = request();
        SubmissionFactsSnapshot facts = facts();
        when(owner.submit(org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.same(request), org.mockito.ArgumentMatchers.same(facts)))
                .thenThrow(new IllegalStateException("owner unavailable"));

        assertThatThrownBy(() -> adapter.submit("user-1", request, facts))
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

    private static SubmissionFactsSnapshot facts() {
        return new SubmissionFactsSnapshot(
                "user-1", true,
                new SubmissionFactsSnapshot.ProblemFacts(
                        101L, "Two Sum", "two-sum", 2, 256, "print(0)"),
                1L, SubmissionFactsSnapshot.CURRENT_SCHEMA_VERSION);
    }
}
