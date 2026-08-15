package com.ulticode.app.api.event;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchDocumentChangedEventContractTest {

    @Test
    void freezesSearchWorkerEnvelope() {
        assertThat(SearchDocumentChangedEventContract.SCHEMA_VERSION).isEqualTo(1);
        assertThat(SearchDocumentChangedEventContract.EVENT_TYPE)
                .isEqualTo("SearchDocumentChanged");
        assertThat(SearchDocumentChangedEventContract.ENVELOPE_FIELDS)
                .isEqualTo(IntegrationEventEnvelopeContract.FIELDS);
        assertThat(SearchDocumentChangedEventContract.SUPPORTED_PUBLISHERS)
                .containsExactlyInAnyOrder("App", "Auth");
        assertThat(SearchDocumentChangedEventContract.SUPPORTED_INDEXES)
                .containsExactlyInAnyOrder("problems", "users", "posts", "solutions");
        assertThat(SearchDocumentChangedEventContract.SUPPORTED_OPERATIONS)
                .containsExactlyInAnyOrder("UPSERT", "DELETE");
    }

    @Test
    void rejectsSensitiveIndexFields() {
        assertThatCode(() -> SearchDocumentChangedEventContract
                .requireSafeDocumentKeys(List.of("id", "title", "difficulty")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> SearchDocumentChangedEventContract
                .requireSafeDocumentKeys(List.of("id", "sourceCode")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceCode");
    }

    @Test
    void rejectsSensitiveFieldsInsideNestedDocuments() {
        assertThatCode(() -> SearchDocumentChangedEventContract.requireSafeDocument(
                Map.of("metadata", List.of(Map.of("labels", List.of(
                        Map.of("displayName", "safe")))))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> SearchDocumentChangedEventContract.requireSafeDocument(
                Map.of("metadata", List.of(Map.of("content", Map.of(
                        "sourceCode", "must-not-cross-the-boundary"))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceCode");
        assertThatThrownBy(() -> SearchDocumentChangedEventContract.requireSafeDocument(
                List.of(Map.of("headers", List.of(Map.of("token", "secret"))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token");
    }
}
