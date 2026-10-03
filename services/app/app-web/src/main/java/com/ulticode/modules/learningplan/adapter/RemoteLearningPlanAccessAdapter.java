package com.ulticode.modules.learningplan.adapter;

import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.learningplan.port.LearningPlanAccessPort;
import com.ulticode.submission.api.dto.SubmissionDetailVO;
import com.ulticode.submission.api.service.SubmissionUserQueryPort;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Boot-shell implementation of {@link LearningPlanAccessPort}.
 *
 * <p>Identity is resolved from the Auth owner's minimal identity projection;
 * the source submission is read through the Submission owner's user query
 * contract, which already applies its own owner predicate. Both checks fail
 * closed: an unavailable or incomplete answer rejects the command rather than
 * permitting a write.
 *
 * <p>This adapter is deliberately <em>not</em> the admin-only
 * {@code DubboIdentityActorAuthorizer} — ordinary USER principals must be
 * accepted, so only the account's live/non-banned state is asserted here.
 */
@Slf4j
@Component
public class RemoteLearningPlanAccessAdapter implements LearningPlanAccessPort {

    @DubboReference(group = "backend-auth",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private IdentityQueryService identityQueryService;

    private final SubmissionUserQueryPort submissionUserQuery;

    /** Spring injects the Submission owner read adapter. */
    @Autowired
    public RemoteLearningPlanAccessAdapter(SubmissionUserQueryPort submissionUserQuery) {
        this.submissionUserQuery = submissionUserQuery;
    }

    /** Package-private constructor for focused unit tests. */
    RemoteLearningPlanAccessAdapter(IdentityQueryService identityQueryService,
                                    SubmissionUserQueryPort submissionUserQuery) {
        this.identityQueryService = identityQueryService;
        this.submissionUserQuery = submissionUserQuery;
    }

    @Override
    public void requireActiveUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED, "Authentication required");
        }
        if (identityQueryService == null) {
            log.warn("IdentityQueryService unavailable; rejecting learning plan command");
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED, "Identity verification unavailable");
        }
        RpcResult<UserIdentityDTO> result;
        try {
            result = identityQueryService.getIdentity(userId);
        } catch (RuntimeException ignored) {
            log.warn("Identity verification RPC failed while authorizing a learning plan command");
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED, "Identity verification unavailable");
        }
        if (result == null || !result.success() || result.data() == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED, "Identity verification failed");
        }
        UserIdentityDTO identity = result.data();
        if (identity.accountId() == null || !identity.accountId().equals(userId) || !identity.active()) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED, "Account is not active");
        }
        if (identity.banned()) {
            throw new BusinessException(BaseErrorCode.FORBIDDEN, "Account is banned");
        }
    }

    @Override
    public void requireOwnedSubmission(String userId, String submissionId) {
        if (submissionId == null || submissionId.isBlank() || submissionUserQuery == null) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Source submission not found");
        }
        SubmissionDetailVO detail;
        try {
            detail = submissionUserQuery.findById(submissionId, userId);
        } catch (RuntimeException ignored) {
            log.warn("Source submission ownership check failed while authorizing a learning plan command");
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED,
                    "Source submission ownership could not be verified");
        }
        if (detail == null) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND,
                    "Source submission not found for the current user");
        }
    }
}
