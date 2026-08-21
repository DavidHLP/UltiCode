package com.ulticode.modules.search.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SearchReadPropertiesTest {

    @Test
    void bindsIndexedModeAndExplicitFallbackPolicy() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.search.read.mode", "indexed")
                .withProperty("app.search.read.fallback-to-database", "true")
                .withProperty("app.search.read.worker-enabled", "true");

        SearchReadProperties properties = Binder.get(environment)
                .bind("app.search.read", Bindable.of(SearchReadProperties.class))
                .orElseThrow(() -> new AssertionError("Search read properties did not bind"));

        assertThat(properties.getMode()).isEqualTo(SearchReadProperties.Mode.INDEXED);
        assertThat(properties.isFallbackToDatabase()).isTrue();
        assertThat(properties.getWorkerEnabled()).isTrue();
    }
}
