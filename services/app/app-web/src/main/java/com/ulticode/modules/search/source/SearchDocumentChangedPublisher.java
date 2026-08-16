package com.ulticode.modules.search.source;

import com.ulticode.app.api.event.SearchDocumentChangedEventContract;
import com.ulticode.app.api.service.ProblemSearchReadPort;
import com.ulticode.modules.event.outbox.IntegrationEventPublisher;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.forum.entity.ForumPost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SEARCH-001 publish seam: source owners (Problem / Forum / Solution)
 * append a durable {@code SearchDocumentChanged} event inside their local
 * write transaction.
 *
 * <p>Each method builds a complete, index-safe document snapshot from the
 * just-written entity, validates it against
 * {@link SearchDocumentChangedEventContract} (recursive key check), and
 * records it in the integration outbox via
 * {@link IntegrationEventPublisher}. The Search worker never reads a
 * business table to fill in missing fields (DEC-011).
 *
 * <p>User documents are published by {@code backend-auth} (AUTH_PUBLISHER);
 * this App-side seam covers the other three sources.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchDocumentChangedPublisher {

    private final IntegrationEventPublisher integrationEventPublisher;
    private final Clock clock;

    /**
     * Publish an UPSERT for a problem document.
     *
     * @param problem the just-written problem entity (id/title/slug/difficulty)
     * @param upsert  {@code true} for create/update, {@code false} for delete
     */
    public void publishProblem(Problem problem, boolean upsert) {
        if (problem == null || problem.getId() == null) {
            return;
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", problem.getId());
        document.put("title", problem.getTitle());
        document.put("slug", problem.getSlug());
        document.put("difficulty", problem.getDifficulty());
        publish(SearchDocumentChangedEventContract.PROBLEMS_INDEX,
                String.valueOf(problem.getId()), upsert ? document : null);
    }

    /**
     * Publish an UPSERT for a forum post document.
     *
     * @param post   the just-written forum post entity (id/title/excerpt/permalink)
     * @param upsert {@code true} for create/update, {@code false} for delete
     */
    public void publishForumPost(ForumPost post, boolean upsert) {
        if (post == null || post.getId() == null) {
            return;
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", post.getId());
        document.put("title", post.getTitle());
        document.put("excerpt", post.getExcerpt());
        document.put("permalink", post.getPermalink());
        publish(SearchDocumentChangedEventContract.POSTS_INDEX,
                post.getId(), upsert ? document : null);
    }

    /**
     * Publish an UPSERT for a solution document.
     *
     * @param solution the just-written solution entity (id/title/summary/problemId)
     * @param upsert   {@code true} for create/update, {@code false} for delete
     */
    public void publishSolution(Solution solution, boolean upsert) {
        if (solution == null || solution.getId() == null) {
            return;
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", solution.getId());
        document.put("title", solution.getTitle());
        document.put("summary", solution.getSummary());
        document.put("problemId", solution.getProblemId());
        publish(SearchDocumentChangedEventContract.SOLUTIONS_INDEX,
                solution.getId(), upsert ? document : null);
    }

    @org.springframework.transaction.annotation.Transactional
    private void publish(String index, String documentId, Map<String, Object> document) {
        Map<String, Object> payload = new LinkedHashMap<>();
            payload.put(SearchDocumentChangedEventContract.INDEX, index);
            payload.put(SearchDocumentChangedEventContract.OPERATION,
                    document == null
                            ? SearchDocumentChangedEventContract.DELETE
                            : SearchDocumentChangedEventContract.UPSERT);
            if (document != null) {
                SearchDocumentChangedEventContract.requireSafeDocument(document);
                payload.put(SearchDocumentChangedEventContract.DOCUMENT, document);
            }
            payload.put(SearchDocumentChangedEventContract.OCCURRED_AT,
                    LocalDateTime.now(clock).toString());

            integrationEventPublisher.publish(
                    SearchDocumentChangedEventContract.APP_PUBLISHER,
                    SearchDocumentChangedEventContract.EVENT_TYPE,
                    documentId,
                    0L,
                    null,
                    null,
                    payload);
            log.debug("Published {} {} event for {}", index,
                    payload.get(SearchDocumentChangedEventContract.OPERATION), documentId);
    }
}