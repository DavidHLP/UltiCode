package com.ulticode.modules.learningplan.adapter;

import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.submission.api.dto.SubmissionDetailVO;
import com.ulticode.submission.api.service.SubmissionUserQueryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RemoteLearningPlanAccessAdapter}: fail-closed identity
 * and source-submission ownership checks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RemoteLearningPlanAccessAdapterTest {

    private static final String USER = "11111111-1111-1111-1111-111111111111";
    private static final String SUBMISSION = "22222222-2222-2222-2222-222222222222";

    @Mock
    private IdentityQueryService identityQueryService;

    @Mock
    private SubmissionUserQueryPort submissionUserQuery;

    private RemoteLearningPlanAccessAdapter adapter() {
        return new RemoteLearningPlanAccessAdapter(identityQueryService, submissionUserQuery);
    }

    private static UserIdentityDTO identity(String accountId, boolean active, boolean banned) {
        return new UserIdentityDTO(accountId, "user", "USER", active, banned);
    }

    @Test
    @DisplayName("active, non-banned identity passes")
    void activeIdentityPasses() {
        when(identityQueryService.getIdentity(USER))
                .thenReturn(RpcResult.success(identity(USER, true, false), "t"));

        assertThatCode(() -> adapter().requireActiveUser(USER)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("missing principal is rejected")
    void missingPrincipalRejected() {
        BusinessException exception = catchThrowableOfType(
                () -> adapter().requireActiveUser(null), BusinessException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(BaseErrorCode.UNAUTHORIZED.code());
    }

    @Test
    @DisplayName("identity RPC failure fails closed")
    void identityRpcFailureFailsClosed() {
        when(identityQueryService.getIdentity(USER)).thenThrow(new RuntimeException("auth down"));

        BusinessException exception = catchThrowableOfType(
                () -> adapter().requireActiveUser(USER), BusinessException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(BaseErrorCode.UNAUTHORIZED.code());
    }

    @Test
    @DisplayName("incomplete identity result fails closed")
    void incompleteIdentityFailsClosed() {
        when(identityQueryService.getIdentity(USER))
                .thenReturn(RpcResult.failure(BaseErrorCode.NOT_FOUND, "t"));

        BusinessException exception = catchThrowableOfType(
                () -> adapter().requireActiveUser(USER), BusinessException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(BaseErrorCode.UNAUTHORIZED.code());
    }

    @Test
    @DisplayName("identity with a mismatched account id fails closed")
    void mismatchedAccountIdFailsClosed() {
        when(identityQueryService.getIdentity(USER))
                .thenReturn(RpcResult.success(identity("other-account", true, false), "t"));

        BusinessException exception = catchThrowableOfType(
                () -> adapter().requireActiveUser(USER), BusinessException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(BaseErrorCode.UNAUTHORIZED.code());
    }

    @Test
    @DisplayName("inactive account is rejected")
    void inactiveAccountRejected() {
        when(identityQueryService.getIdentity(USER))
                .thenReturn(RpcResult.success(identity(USER, false, false), "t"));

        BusinessException exception = catchThrowableOfType(
                () -> adapter().requireActiveUser(USER), BusinessException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(BaseErrorCode.UNAUTHORIZED.code());
    }

    @Test
    @DisplayName("banned account is forbidden")
    void bannedAccountForbidden() {
        when(identityQueryService.getIdentity(USER))
                .thenReturn(RpcResult.success(identity(USER, true, true), "t"));

        BusinessException exception = catchThrowableOfType(
                () -> adapter().requireActiveUser(USER), BusinessException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(BaseErrorCode.FORBIDDEN.code());
    }

    @Test
    @DisplayName("owned source submission passes")
    void ownedSubmissionPasses() {
        when(submissionUserQuery.findById(SUBMISSION, USER)).thenReturn(new SubmissionDetailVO());

        assertThatCode(() -> adapter().requireOwnedSubmission(USER, SUBMISSION))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("missing or foreign source submission is hidden as not found")
    void missingOwnedSubmissionRejected() {
        when(submissionUserQuery.findById(SUBMISSION, USER)).thenReturn(null);

        BusinessException exception = catchThrowableOfType(
                () -> adapter().requireOwnedSubmission(USER, SUBMISSION), BusinessException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(BaseErrorCode.NOT_FOUND.code());
    }

    @Test
    @DisplayName("submission lookup failure fails closed")
    void submissionLookupFailureFailsClosed() {
        when(submissionUserQuery.findById(SUBMISSION, USER)).thenThrow(new RuntimeException("submission down"));

        BusinessException exception = catchThrowableOfType(
                () -> adapter().requireOwnedSubmission(USER, SUBMISSION), BusinessException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(BaseErrorCode.UNAUTHORIZED.code());
    }
}
