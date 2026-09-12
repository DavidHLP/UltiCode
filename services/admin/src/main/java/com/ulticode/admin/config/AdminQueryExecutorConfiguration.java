package com.ulticode.admin.config;

import com.ulticode.modules.admin.port.adapter.CancellableQueryExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Owns the bounded executor used by Admin's cross-owner query fan-out. */
@Configuration(proxyBeanMethods = false)
public class AdminQueryExecutorConfiguration {

    @Bean(destroyMethod = "close")
    public CancellableQueryExecutor adminEnrichmentQueryExecutor() {
        return new CancellableQueryExecutor("admin-user-enrichment-query", 2);
    }
}
