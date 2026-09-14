package com.ulticode.admin.config;

import com.ulticode.common.time.TimeSource;
import com.ulticode.modules.admin.port.adapter.AdminQueryDeadline;
import com.ulticode.modules.admin.port.adapter.CancellableQueryExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Owns Admin query lifecycles while keeping per-use-case pools isolated. */
@Configuration(proxyBeanMethods = false)
public class AdminQueryExecutorConfiguration {

    @Bean
    public AdminQueryDeadline adminQueryDeadline(TimeSource timeSource) {
        return new AdminQueryDeadline(timeSource);
    }

    @Bean(name = "adminUserEnrichmentQueryExecutor", destroyMethod = "close")
    public CancellableQueryExecutor adminUserEnrichmentQueryExecutor() {
        return new CancellableQueryExecutor("admin-user-enrichment-query", 2);
    }

    @Bean(name = "adminAnalyticsQueryExecutor", destroyMethod = "close")
    public CancellableQueryExecutor adminAnalyticsQueryExecutor() {
        return new CancellableQueryExecutor("admin-analytics-query", 6);
    }

    @Bean(name = "adminDashboardQueryExecutor", destroyMethod = "close")
    public CancellableQueryExecutor adminDashboardQueryExecutor() {
        return new CancellableQueryExecutor("admin-dashboard-query", 4);
    }

    @Bean(name = "adminUserDetailQueryExecutor", destroyMethod = "close")
    public CancellableQueryExecutor adminUserDetailQueryExecutor() {
        return new CancellableQueryExecutor("admin-user-detail-query", 4);
    }
}
