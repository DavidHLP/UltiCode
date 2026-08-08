package com.ulticode.modules.search.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MeiliSearch configuration.
 * Only creates the client when MeiliSearch is explicitly enabled.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "meilisearch.enabled", havingValue = "true")
public class MeiliSearchConfig {

    @Value("${meilisearch.host}")
    private String host;

    @Value("${meilisearch.api-key:}")
    private String apiKey;

    /**
     * Create MeiliSearch client bean.
     *
     * @return the MeiliSearch client
     */
    @Bean
    public Client meiliSearchClient() {
        log.info("Initializing MeiliSearch client for host: {}", host);
        return new Client(new Config(host, apiKey));
    }
}
