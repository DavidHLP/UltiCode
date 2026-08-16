package com.ulticode.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Backend-search indexing worker (DEC-011, SEARCH-002).
 *
 * <p>No HTTP surface, no business database, no Dubbo: the worker consumes
 * {@code SearchDocumentChanged} events from {@code stream:integration} and is
 * the sole MeiliSearch index writer. At-least-once delivery is backed by the
 * Redis Streams pending-entries list (PEL) with bounded retry and a DLQ; every
 * MeiliSearch write is idempotent by document id, so replay is safe.
 */
@SpringBootApplication
@EnableScheduling
public class SearchWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchWorkerApplication.class, args);
    }
}
