package com.ulticode.modules.problem.service;

import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.search.source.SearchDocumentChangedPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProblemIndexRefresherTest {

    @Mock
    private SearchDocumentChangedPublisher searchPublisher;

    private ProblemIndexRefresher refresher;

    @BeforeEach
    void setUp() {
        refresher = new ProblemIndexRefresher(searchPublisher);
    }

    @Test
    void publishedActiveProblemIsUpserted() {
        Problem problem = problem(true, false);

        refresher.publish(problem);

        verify(searchPublisher).publishProblem(problem, true);
    }

    @Test
    void unpublishedProblemIsTombstoned() {
        Problem problem = problem(false, false);

        refresher.publish(problem);

        verify(searchPublisher).publishProblem(problem, false);
    }

    @Test
    void deletedProblemIsTombstonedEvenWhenPublished() {
        Problem problem = problem(true, true);

        refresher.publish(problem);

        verify(searchPublisher).publishProblem(problem, false);
    }

    @Test
    void nullProblemDoesNotPublish() {
        refresher.publish(null);

        verifyNoInteractions(searchPublisher);
    }

    private static Problem problem(boolean published, boolean deleted) {
        Problem problem = new Problem();
        problem.setId(1L);
        problem.setIsPublished(published);
        problem.setIsDeleted(deleted);
        return problem;
    }
}
