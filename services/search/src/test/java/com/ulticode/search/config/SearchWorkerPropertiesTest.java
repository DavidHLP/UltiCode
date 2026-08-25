package com.ulticode.search.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review 2026-08-25 FINAL P1: worker scale-out contract — consumer identity
 * must be instance-unique by default so replicas never share one PEL owner.
 */
@DisplayName("SearchWorkerProperties.resolvedConsumerName()")
class SearchWorkerPropertiesTest {

    @Test
    @DisplayName("derives an instance-unique name from the group when unset")
    void derivesUniqueNameWhenUnset() {
        SearchWorkerProperties props = new SearchWorkerProperties();

        String resolved = props.resolvedConsumerName();

        assertThat(resolved).startsWith("search-worker-");
        assertThat(resolved).isNotEqualTo("search-worker-");
        // hostname-based suffix is stable within this JVM
        assertThat(props.resolvedConsumerName()).isEqualTo(resolved);
    }

    @Test
    @DisplayName("an explicit consumer-name always wins")
    void explicitNameWins() {
        SearchWorkerProperties props = new SearchWorkerProperties();
        props.setConsumerName("fixed-worker");

        assertThat(props.resolvedConsumerName()).isEqualTo("fixed-worker");
    }

    @Test
    @DisplayName("blank configured value falls back to the derived name")
    void blankFallsBackToDerived() {
        SearchWorkerProperties props = new SearchWorkerProperties();
        props.setConsumerName("   ");

        assertThat(props.resolvedConsumerName()).startsWith("search-worker-");
    }
}
