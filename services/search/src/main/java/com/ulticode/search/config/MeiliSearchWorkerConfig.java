package com.ulticode.search.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MeiliSearch client for the indexing worker.
 *
 * <p>Created only when the worker is enabled ({@code search.worker.enabled}),
 * so unit-test contexts boot without MeiliSearch.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "search.worker.enabled", havingValue = "true")
public class MeiliSearchWorkerConfig {

    @Value("${meilisearch.host}")
    private String host;

    @Value("${meilisearch.api-key:}")
    private String apiKey;

    @Bean
    public Client searchWorkerMeiliSearchClient() {
        log.info("Initializing search-worker MeiliSearch client for host: {}", host);
        return new Client(new Config(host, apiKey));
    }
    @Bean
    public com.ulticode.search.adapter.SearchIndex searchIndex(Client client) {
        return new com.ulticode.search.adapter.MeiliSearchIndexAdapter(client);
    }
}
