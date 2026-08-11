package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.service.ProblemOwnerPort;
import com.ulticode.modules.problem.port.DefaultProblemOwnerPort;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * Exposes the problem owner's moderation/import write boundary to backend-admin.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ProblemOwnerProvider implements ProblemOwnerPort {

    private final DefaultProblemOwnerPort delegate;

    @Override
    public String resolveAuthorId(String id) {
        return delegate.resolveAuthorId(id);
    }

    @Override
    public void updateModerationFlag(String id, boolean isFlagged, String reason) {
        delegate.updateModerationFlag(id, isFlagged, reason);
    }

    @Override
    public void flagProblem(Long id, String reason, String reportedBy) {
        delegate.flagProblem(id, reason, reportedBy);
    }

    @Override
    public void moderateProblem(Long id, String status, String notes, String reviewedBy) {
        delegate.moderateProblem(id, status, notes, reviewedBy);
    }

    @Override
    public int restoreDeletedByIds(List<Long> ids) {
        return delegate.restoreDeletedByIds(ids);
    }

    @Override
    public int moderateProblems(List<Long> ids, String status, String notes, String reviewedBy) {
        return delegate.moderateProblems(ids, status, notes, reviewedBy);
    }

    @Override
    public void updateDifficulty(Long id, String difficulty) {
        delegate.updateDifficulty(id, difficulty);
    }

    @Override
    public void insertImportedProblem(String slug, String title, String difficulty, String status,
                                      Boolean isPremium, Boolean isPublished) {
        delegate.insertImportedProblem(slug, title, difficulty, status, isPremium, isPublished);
    }

    @Override
    public void applyImportedUpdate(Long id, String title, String difficulty, String status,
                                    Boolean isPremium, Boolean isPublished) {
        delegate.applyImportedUpdate(id, title, difficulty, status, isPremium, isPublished);
    }

    @Override
    public List<ImportWriteResult> applyImportedBatch(List<ImportWriteRequest> requests) {
        if (requests != null && requests.size() > MAX_IMPORT_SIZE) {
            throw new IllegalArgumentException("Too many problem import writes");
        }
        return delegate.applyImportedBatch(requests);
    }
}
