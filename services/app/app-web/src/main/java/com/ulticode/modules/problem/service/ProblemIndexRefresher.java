package com.ulticode.modules.problem.service;

import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.search.source.SearchDocumentChangedPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * App-side policy for publishing Problem search changes.
 *
 * <p>An active published problem is upserted; unpublished or deleted
 * problems are tombstoned. Callers only provide the aggregate snapshot.
 */
@Component
@RequiredArgsConstructor
public class ProblemIndexRefresher {

    private final SearchDocumentChangedPublisher searchPublisher;

    public void publish(Problem problem) {
        if (problem == null) {
            return;
        }
        boolean upsert = Boolean.TRUE.equals(problem.getIsPublished())
                && !Boolean.TRUE.equals(problem.getIsDeleted());
        searchPublisher.publishProblem(problem, upsert);
    }
}
