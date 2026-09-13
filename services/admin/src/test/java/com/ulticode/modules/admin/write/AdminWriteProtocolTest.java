package com.ulticode.modules.admin.write;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminWriteProtocolTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Test
    void envelopeUsesTrimmedSuppliedKeyAndStableMetadata() {
        when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(false);

        AdminWriteEnvelope first = AdminWriteEnvelope.envelope(
                "contest-create", "  retry-1  ", "admin-1", currentUserProvider, "contest create");
        AdminWriteEnvelope retry = AdminWriteEnvelope.envelope(
                "contest-create", "retry-1", "admin-1", currentUserProvider, "contest create");

        assertThat(first.commandId()).isEqualTo(retry.commandId());
        assertThat(first.idempotency().idempotencyKey()).isEqualTo("retry-1");
        assertThat(first.idempotency().fingerprint()).isNull();
        assertThat(first.idempotency().issuedBy()).isNull();
        assertThat(first.actor().actorType()).isEqualTo("ADMIN");
        assertThat(first.actor().actorId()).isEqualTo("admin-1");
        assertThat(first.actor().delegatorId()).isEqualTo("admin-1");
        assertThat(first.actor().rationale()).isEqualTo("contest create");
        assertThat(first.trace()).isNotNull().matches(trace -> trace.hasTraceId());
    }

    @Test
    void envelopeUsesAdminActorsForSuperAdminAndMintsMissingKey() {
        when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(true);

        AdminWriteEnvelope first = AdminWriteEnvelope.envelope(
                "forum-tag-create", null, "super-1", currentUserProvider, "forum tag create");
        AdminWriteEnvelope second = AdminWriteEnvelope.envelope(
                "forum-tag-create", "", "super-1", currentUserProvider, "forum tag create");

        assertThat(first.idempotency().hasKey()).isTrue();
        assertThat(second.idempotency().hasKey()).isTrue();
        assertThat(first.idempotency().idempotencyKey())
                .isNotEqualTo(second.idempotency().idempotencyKey());
        assertThat(first.actor().actorType()).isEqualTo("SUPER_ADMIN");
        assertThat(first.commandId()).isNotBlank();
        assertThat(first.trace().traceId()).isNotBlank();
    }

    @Test
    void envelopeRejectsMissingActorAndOversizedKey() {
        assertThatThrownBy(() -> AdminWriteEnvelope.envelope(
                "problem-create", null, " ", currentUserProvider, "problem create"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(AdminErrorCode.UNAUTHORIZED);

        assertThatThrownBy(() -> AdminWriteEnvelope.envelope(
                "problem-create", "x".repeat(121), "admin-1", currentUserProvider, "problem create"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(AdminErrorCode.BAD_REQUEST);
    }

    @Test
    void mapperAppliesCommonFailureRowsToEveryOwner() {
        for (AdminOwnerErrorMapper.Owner owner : AdminOwnerErrorMapper.Owner.values()) {
            assertMapped(owner, AppErrorCode.BAD_REQUEST.code(),
                    owner == AdminOwnerErrorMapper.Owner.PROBLEM_LIST
                            ? AdminErrorCode.VALIDATION_FAILED : AdminErrorCode.BAD_REQUEST);
            assertMapped(owner, AppErrorCode.UNAUTHORIZED.code(), AdminErrorCode.UNAUTHORIZED);
            assertMapped(owner, AppErrorCode.FORBIDDEN.code(), AdminErrorCode.FORBIDDEN);
            assertMapped(owner, AppErrorCode.VERSION_CONFLICT.code(), AdminErrorCode.CONFLICT);
            assertMapped(owner, AppErrorCode.CONTENT_STATE_CONFLICT.code(), AdminErrorCode.CONFLICT);
            assertMapped(owner, AppErrorCode.IDEMPOTENCY_KEY_CONFLICT.code(), AdminErrorCode.CONFLICT);
        }
    }

    @Test
    void mapperPreservesOwnerNotFoundAndSpecialConflictRows() {
        assertMapped(AdminOwnerErrorMapper.Owner.CONTEST,
                AppErrorCode.CONTENT_NOT_FOUND.code(), AdminErrorCode.CONTEST_NOT_FOUND);
        assertMapped(AdminOwnerErrorMapper.Owner.PROBLEM,
                AppErrorCode.CONTENT_NOT_FOUND.code(), AdminErrorCode.PROBLEM_NOT_FOUND);
        assertMapped(AdminOwnerErrorMapper.Owner.SUBMISSION,
                AppErrorCode.CONTENT_NOT_FOUND.code(), AdminErrorCode.SUBMISSION_NOT_FOUND);
        assertMapped(AdminOwnerErrorMapper.Owner.FORUM_TAG,
                AppErrorCode.CONTENT_NOT_FOUND.code(), AdminErrorCode.FORUM_TAG_NOT_FOUND);
        assertMapped(AdminOwnerErrorMapper.Owner.PROBLEM_LIST,
                AppErrorCode.CONTENT_NOT_FOUND.code(), AdminErrorCode.PROBLEM_LIST_NOT_FOUND);
        assertMapped(AdminOwnerErrorMapper.Owner.NOTIFICATION,
                AppErrorCode.CONTENT_NOT_FOUND.code(), AdminErrorCode.NOT_FOUND);
        assertMapped(AdminOwnerErrorMapper.Owner.PROBLEM_LIST,
                AppErrorCode.PROBLEM_NOT_FOUND.code(), AdminErrorCode.PROBLEM_NOT_FOUND);
        assertMapped(AdminOwnerErrorMapper.Owner.PROBLEM_LIST,
                AppErrorCode.PROBLEM_LIST_PROBLEM_DUPLICATE.code(),
                AdminErrorCode.PROBLEM_LIST_PROBLEM_DUPLICATE);
        assertMapped(AdminOwnerErrorMapper.Owner.FORUM_TAG,
                AppErrorCode.FORUM_TAG_NAME_CONFLICT.code(), AdminErrorCode.FORUM_TAG_NAME_EXISTS);
        assertMapped(AdminOwnerErrorMapper.Owner.FORUM_TAG,
                AppErrorCode.FORUM_TAG_SLUG_CONFLICT.code(), AdminErrorCode.FORUM_TAG_SLUG_EXISTS);
    }

    @Test
    void mapperFailsClosedForMissingAndUnknownPayloads() {
        assertThat(AdminOwnerErrorMapper.mapOwnerError(null, failure(40401)).getErrorCode())
                .isEqualTo(AdminErrorCode.UNKNOWN_ERROR);

        assertThat(AdminOwnerErrorMapper.mapOwnerError(
                AdminOwnerErrorMapper.Owner.PROBLEM, null).getErrorCode())
                .isEqualTo(AdminErrorCode.UNKNOWN_ERROR);

        RpcResult<?> missingError = new RpcResult<>(
                false, null, null, null, "trace-1", null, null);
        assertThat(AdminOwnerErrorMapper.mapOwnerError(
                AdminOwnerErrorMapper.Owner.PROBLEM, missingError).getErrorCode())
                .isEqualTo(AdminErrorCode.UNKNOWN_ERROR);

        BusinessException unknown = AdminOwnerErrorMapper.mapOwnerError(
                AdminOwnerErrorMapper.Owner.PROBLEM, failure(59999));
        assertThat(unknown.getErrorCode()).isEqualTo(AdminErrorCode.UNKNOWN_ERROR);
        assertThat(unknown.getMessage()).isEqualTo("source-error");
    }

    private static void assertMapped(
            AdminOwnerErrorMapper.Owner owner, int code, AdminErrorCode expected) {
        assertThat(AdminOwnerErrorMapper.mapOwnerError(owner, failure(code)).getErrorCode())
                .as("owner=%s code=%s", owner, code)
                .isEqualTo(expected);
    }

    private static RpcResult<?> failure(int code) {
        return new RpcResult<>(
                false,
                null,
                null,
                new RpcResult.ErrorPayload("app", code, "source-error"),
                "trace-1",
                null,
                null);
    }
}
