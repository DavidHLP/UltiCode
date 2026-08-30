package com.ulticode.submission.dubbo.provider;

import com.ulticode.submission.api.dto.SubmissionUserReferenceCountDTO;
import com.ulticode.submission.api.service.SubmissionReconciliationReadPort;
import com.ulticode.modules.submission.mapper.SubmissionReconciliationReadMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDateTime;
import java.util.List;

/** Exposes bounded Submission-owned user-reference facts to reconciliation. */
@DubboService(group = "backend-submission", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionReconciliationReadProvider implements SubmissionReconciliationReadPort {

    private final SubmissionReconciliationReadMapper mapper;

    @Override
    public List<SubmissionUserReferenceCountDTO> findUserReferenceCounts(
            String afterAccountId,
            LocalDateTime createdSince,
            int limit) {
        if (afterAccountId == null || afterAccountId.length() > 40
                || (!afterAccountId.isEmpty() && afterAccountId.isBlank())
                || limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Invalid Submission reconciliation page");
        }
        List<SubmissionUserReferenceCountDTO> facts = mapper.findUserReferenceCounts(
                afterAccountId, createdSince, limit);
        if (facts == null) {
            throw new IllegalStateException("Submission reconciliation facts unavailable");
        }
        if (facts.size() > limit) {
            throw new IllegalStateException("Submission reconciliation page exceeds limit");
        }
        String previous = afterAccountId;
        for (SubmissionUserReferenceCountDTO fact : facts) {
            if (fact == null || fact.accountId() == null || fact.accountId().isBlank()
                    || fact.accountId().length() > 40 || fact.rowCount() < 0
                    || fact.accountId().compareTo(previous) <= 0) {
                throw new IllegalStateException("Invalid Submission reconciliation facts");
            }
            previous = fact.accountId();
        }
        return List.copyOf(facts);
    }
}
