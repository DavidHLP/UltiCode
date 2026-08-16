package com.ulticode.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEARCH-002 contract: the worker boots with no web server and no Redis/
 * MeiliSearch dependency (worker is disabled by default in tests). The worker
 * bean is absent here; the enabled path is covered by
 * {@link SearchDocumentIndexWorkerTest}.
 */
@SpringBootTest
@DisplayName("SearchWorkerApplication boot contract")
class SearchWorkerApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("context loads with web-application-type none and no search worker bean")
    void contextLoadsWithoutWebOrWorker() {
        assertThat(context).isNotNull();
        assertThat(context.containsBean("searchDocumentIndexWorker")).isFalse();
        assertThat(context.getBean(org.springframework.core.env.Environment.class)
                .getProperty("spring.main.web-application-type")).isEqualTo("none");
    }
}
