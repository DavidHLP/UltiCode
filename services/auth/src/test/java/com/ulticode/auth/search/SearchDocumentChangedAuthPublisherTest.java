package com.ulticode.auth.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ulticode.common.event.SearchDocumentChangedEventContract;
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
@DisplayName("SearchDocumentChangedAuthPublisher")
class SearchDocumentChangedAuthPublisherTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneId.of("UTC"));

    @Mock private SearchDocumentChangedOutboxMapper outboxMapper;

    private SearchDocumentChangedAuthPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SearchDocumentChangedAuthPublisher(outboxMapper, FIXED_CLOCK);
    }

    @Test
    @DisplayName("upsert stores a complete index-safe user document in the outbox")
    void publishUser_upsertWritesSafeDocument() {
        publisher.publishUser("u-1", "alice", null, null, true);

        ArgumentCaptor<SearchDocumentChangedOutboxRecord> captor =
                ArgumentCaptor.forClass(SearchDocumentChangedOutboxRecord.class);
        verify(outboxMapper).insert(captor.capture());
        SearchDocumentChangedOutboxRecord record = captor.getValue();

        assertThat(record.getOwner()).isEqualTo(SearchDocumentChangedEventContract.AUTH_PUBLISHER);
        assertThat(record.getAggregateId()).isEqualTo("u-1");
        assertThat(record.getEventType()).isEqualTo(SearchDocumentChangedEventContract.EVENT_TYPE);
        assertThat(record.getSchemaVersion()).isEqualTo(SearchDocumentChangedEventContract.SCHEMA_VERSION);
        assertThat(record.getPayload().get(SearchDocumentChangedEventContract.INDEX))
                .isEqualTo(SearchDocumentChangedEventContract.USERS_INDEX);
        assertThat(record.getPayload().get(SearchDocumentChangedEventContract.OPERATION))
                .isEqualTo(SearchDocumentChangedEventContract.UPSERT);
        @SuppressWarnings("unchecked")
        Map<String, Object> document =
                (Map<String, Object>) record.getPayload().get(SearchDocumentChangedEventContract.DOCUMENT);
        assertThat(document).containsEntry("id", "u-1").containsEntry("username", "alice");
        assertThat(document).doesNotContainKey("name").doesNotContainKey("avatar");
    }

    @Test
    @DisplayName("upsert includes name/avatar when provided")
    void publishUser_upsertIncludesProfileFields() {
        publisher.publishUser("u-1", "alice", "Alice", "/a.png", true);

        ArgumentCaptor<SearchDocumentChangedOutboxRecord> captor =
                ArgumentCaptor.forClass(SearchDocumentChangedOutboxRecord.class);
        verify(outboxMapper).insert(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> document = (Map<String, Object>)
                captor.getValue().getPayload().get(SearchDocumentChangedEventContract.DOCUMENT);
        assertThat(document).containsEntry("name", "Alice").containsEntry("avatar", "/a.png");
    }

    @Test
    @DisplayName("delete stores a tombstone with no document payload")
    void publishUser_deleteStoresTombstone() {
        publisher.publishUser("u-1", null, null, null, false);

        ArgumentCaptor<SearchDocumentChangedOutboxRecord> captor =
                ArgumentCaptor.forClass(SearchDocumentChangedOutboxRecord.class);
        verify(outboxMapper).insert(captor.capture());
        assertThat(captor.getValue().getPayload().get(SearchDocumentChangedEventContract.OPERATION))
                .isEqualTo(SearchDocumentChangedEventContract.DELETE);
        assertThat(captor.getValue().getPayload()).doesNotContainKey(SearchDocumentChangedEventContract.DOCUMENT);
    }

    @Test
    @DisplayName("blank aggregate id is ignored")
    void publishUser_blankIdIsIgnored() {
        publisher.publishUser("  ", "alice", null, null, true);
        verify(outboxMapper, never()).insert(any());
    }
}
