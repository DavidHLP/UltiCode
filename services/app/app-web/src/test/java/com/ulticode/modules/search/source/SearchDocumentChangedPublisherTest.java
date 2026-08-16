package com.ulticode.modules.search.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.ulticode.common.event.SearchDocumentChangedEventContract;
import com.ulticode.modules.event.outbox.IntegrationEventPublisher;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.solution.entity.Solution;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchDocumentChangedPublisher")
class SearchDocumentChangedPublisherTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneId.of("UTC"));
    private static final long FIXED_VERSION_MILLIS = 1_786_874_400_000L;

    @Mock private IntegrationEventPublisher integrationEventPublisher;

    private SearchDocumentChangedPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SearchDocumentChangedPublisher(integrationEventPublisher, FIXED_CLOCK);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePayload(String aggregateId) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(integrationEventPublisher).publish(
                eq(SearchDocumentChangedEventContract.APP_PUBLISHER),
                eq(SearchDocumentChangedEventContract.EVENT_TYPE),
                eq(aggregateId),
                eq(FIXED_VERSION_MILLIS),
                eq(null),
                eq(null),
                captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("problem UPSERT publishes version = event instant and full payload")
    void publishProblem_upsertCarriesVersionAndDocument() {
        Problem problem = new Problem();
        problem.setId(1L);
        problem.setTitle("Two Sum");
        problem.setSlug("two-sum");
        problem.setDifficulty("Easy");

        publisher.publishProblem(problem, true);

        Map<String, Object> payload = capturePayload("1");
        assertThat(payload.get(SearchDocumentChangedEventContract.INDEX))
                .isEqualTo(SearchDocumentChangedEventContract.PROBLEMS_INDEX);
        assertThat(payload.get(SearchDocumentChangedEventContract.OPERATION))
                .isEqualTo(SearchDocumentChangedEventContract.UPSERT);
        assertThat(payload.get(SearchDocumentChangedEventContract.OCCURRED_AT))
                .isEqualTo("2026-08-16T10:00");
        Map<String, Object> document =
                (Map<String, Object>) payload.get(SearchDocumentChangedEventContract.DOCUMENT);
        assertThat(document)
                .containsEntry("id", 1L)
                .containsEntry("title", "Two Sum")
                .containsEntry("slug", "two-sum")
                .containsEntry("difficulty", "Easy");
    }

    @Test
    @DisplayName("forum post DELETE publishes a tombstone without document")
    void publishForumPost_deletePublishesTombstone() {
        ForumPost post = new ForumPost();
        post.setId("p-1");

        publisher.publishForumPost(post, false);

        Map<String, Object> payload = capturePayload("p-1");
        assertThat(payload.get(SearchDocumentChangedEventContract.INDEX))
                .isEqualTo(SearchDocumentChangedEventContract.POSTS_INDEX);
        assertThat(payload.get(SearchDocumentChangedEventContract.OPERATION))
                .isEqualTo(SearchDocumentChangedEventContract.DELETE);
        assertThat(payload).doesNotContainKey(SearchDocumentChangedEventContract.DOCUMENT);
    }

    @Test
    @DisplayName("solution UPSERT keeps version semantics for replay ordering")
    void publishSolution_upsertKeepsVersionSemantics() {
        Solution solution = new Solution();
        solution.setId("s-1");
        solution.setProblemId(7L);
        solution.setTitle("Clean two-pass");

        publisher.publishSolution(solution, true);

        Map<String, Object> payload = capturePayload("s-1");
        Map<String, Object> document =
                (Map<String, Object>) payload.get(SearchDocumentChangedEventContract.DOCUMENT);
        assertThat(document).containsEntry("id", "s-1").containsEntry("problemId", 7L);
    }

    @Test
    @DisplayName("user UPSERT from App profile enrichment carries the same version semantics")
    void publishUser_upsertCarriesVersion() {
        publisher.publishUser("u-1", "alice", "Alice", "/a.png", true);

        Map<String, Object> payload = capturePayload("u-1");
        assertThat(payload.get(SearchDocumentChangedEventContract.INDEX))
                .isEqualTo(SearchDocumentChangedEventContract.USERS_INDEX);
        Map<String, Object> document =
                (Map<String, Object>) payload.get(SearchDocumentChangedEventContract.DOCUMENT);
        assertThat(document)
                .containsEntry("id", "u-1")
                .containsEntry("username", "alice")
                .containsEntry("name", "Alice")
                .containsEntry("avatar", "/a.png");
    }
}
