package com.ulticode.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
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
                Map.of("headers", List.of(Map.of("token", "secret")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token");
    }

    @Test
    void rejectsScalarRootsAndNonStringKeys() {
        assertThatThrownBy(() -> SearchDocumentChangedEventContract.requireSafeDocument(42))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON object");
        assertThatThrownBy(() -> SearchDocumentChangedEventContract.requireSafeDocument("title"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON object");
        assertThatThrownBy(() -> SearchDocumentChangedEventContract.requireSafeDocument(
                List.of("id", "title")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON object");
        assertThatThrownBy(() -> SearchDocumentChangedEventContract.requireSafeDocument(
                Map.of("nested", Map.of(1, "numeric-key"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be strings");
    }

    @Test
    void freezesSearchDocumentWireShapeAsJson() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(SearchDocumentChangedEventContract.INDEX,
                SearchDocumentChangedEventContract.PROBLEMS_INDEX);
        payload.put(SearchDocumentChangedEventContract.OPERATION,
                SearchDocumentChangedEventContract.UPSERT);
        payload.put(SearchDocumentChangedEventContract.OCCURRED_AT, "2026-08-16T00:00:00Z");
        payload.put(SearchDocumentChangedEventContract.DOCUMENT,
                Map.of("id", "p-1", "title", "A+B", "tags", List.of("math")));

        ObjectMapper mapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        Map<String, Object> node = mapper.readValue(
                mapper.writeValueAsBytes(payload), Map.class);

        assertThat(node.keySet()).containsExactlyInAnyOrder(
                SearchDocumentChangedEventContract.INDEX,
                SearchDocumentChangedEventContract.OPERATION,
                SearchDocumentChangedEventContract.OCCURRED_AT,
                SearchDocumentChangedEventContract.DOCUMENT);
        assertThat(node.get(SearchDocumentChangedEventContract.INDEX))
                .isEqualTo(SearchDocumentChangedEventContract.PROBLEMS_INDEX);
        assertThat(node.get(SearchDocumentChangedEventContract.OPERATION))
                .isEqualTo(SearchDocumentChangedEventContract.UPSERT);
        assertThat(node.get(SearchDocumentChangedEventContract.DOCUMENT))
                .isEqualTo(Map.of("id", "p-1", "title", "A+B", "tags", List.of("math")));
    }
}
