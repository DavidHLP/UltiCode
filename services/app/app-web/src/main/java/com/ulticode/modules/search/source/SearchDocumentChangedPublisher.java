package com.ulticode.modules.search.source;

import com.ulticode.common.event.SearchDocumentChangedEventContract;
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
        publish(SearchDocumentChangedEventContract.PROBLEMS_INDEX,
                String.valueOf(problem.getId()),
                upsert ? SearchDocumentBuilders.problem(problem) : null);
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
        publish(SearchDocumentChangedEventContract.POSTS_INDEX,
                post.getId(), upsert ? SearchDocumentBuilders.forumPost(post) : null);
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
        publish(SearchDocumentChangedEventContract.SOLUTIONS_INDEX,
                solution.getId(), upsert ? SearchDocumentBuilders.solution(solution) : null);
    }

    /**
     * Publish an UPSERT or DELETE for a user document (SEARCH-001 slice-b).
     *
     * <p>Auth owns the {@code users} index. App profile writes publish the
     * complete user projection with the Auth owner tag so the Search worker
     * enforces one owner per index while the profile migration is staged.
     *
     * @param aggregateId the user id (document id)
     * @param username    display username ({@code null} for DELETE)
     * @param name        optional display name
     * @param avatar      optional avatar URL
     * @param upsert      {@code true} for create/update, {@code false} for delete tombstone
     */
    public void publishUser(String aggregateId, String username, String name, String avatar,
                            boolean upsert) {
        if (aggregateId == null || aggregateId.isBlank()) {
            return;
        }
        publishForOwner(SearchDocumentChangedEventContract.AUTH_PUBLISHER,
                SearchDocumentChangedEventContract.USERS_INDEX, aggregateId,
                upsert ? SearchDocumentBuilders.user(aggregateId, username, name, avatar) : null,
                clock.instant().toEpochMilli());
    }

    /**
     * Publish a backfill UPSERT or DELETE with an explicit document version
     * (DEC-016/017). The version is the row's last-change epoch millis, so a
     * snapshot can never overwrite a newer live write at the worker ledger.
     * Each call commits its own outbox row transaction; a failed run is safe
     * to re-run because backfill converges idempotently.
     *
     * @param index        allowlisted index ({@code problems|users|posts|solutions})
     * @param documentId   the document id (envelope aggregate id)
     * @param versionMillis row last-change epoch millis
     * @param document     full index-safe document, or {@code null} for DELETE
     */
    @org.springframework.transaction.annotation.Transactional
    public void publishBackfill(String index, String documentId, long versionMillis,
                                Map<String, Object> document) {
        if (documentId == null || documentId.isBlank()) {
            return;
        }
        if (document != null) {
            SearchDocumentChangedEventContract.requireSafeDocument(document);
        }
        publishForOwner(ownerForIndex(index), index, documentId, document, versionMillis);
    }

    private void publish(String index, String documentId, Map<String, Object> document) {
        publishForOwner(ownerForIndex(index), index, documentId, document, clock.instant().toEpochMilli());
    }

    private void publishForOwner(String owner, String index, String documentId,
                                 Map<String, Object> document, long versionMillis) {
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
        LocalDateTime occurredAt = LocalDateTime.now(clock);
        payload.put(SearchDocumentChangedEventContract.OCCURRED_AT, occurredAt.toString());

        integrationEventPublisher.publish(
                owner,
                SearchDocumentChangedEventContract.EVENT_TYPE,
                documentId,
                versionMillis,
                null,
                null,
                payload);
        log.debug("Published {} {} event for {} at version {}", index,
                payload.get(SearchDocumentChangedEventContract.OPERATION), documentId, versionMillis);
    }
    private static String ownerForIndex(String index) {
        return SearchDocumentChangedEventContract.USERS_INDEX.equals(index)
                ? SearchDocumentChangedEventContract.AUTH_PUBLISHER
                : SearchDocumentChangedEventContract.APP_PUBLISHER;
    }
}