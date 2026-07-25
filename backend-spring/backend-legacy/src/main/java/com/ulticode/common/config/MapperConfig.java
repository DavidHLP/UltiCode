package com.ulticode.common.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis mapper scanning configuration.
 *
 * <p>Moved from UlticodeBackendApplication to a dedicated @Configuration class
 * so that @WebMvcTest slice tests can exclude it via excludeFilters,
 * avoiding the need for a full DataSource/MyBatis setup in controller tests.</p>
 *
 * <p>Uses {@code **} (recursive) instead of {@code *} (single-segment) to
 * match nested mapper sub-packages such as
 * {@code com.ulticode.modules.notification.ledger.mapper} (M4a) and
 * {@code com.ulticode.modules.queue.outbox.mapper} (M3a). The single-asterisk
 * form {@code com.ulticode.modules.*.mapper} only matches one segment between
 * {@code modules} and {@code mapper}, silently dropping nested mappers and
 * causing {@code NoSuchBeanDefinitionException} at runtime. Spring's
 * {@code PathMatchingAntPathMatcher} treats {@code **} as recursive.</p>
 */
@Configuration
@MapperScan("com.ulticode.modules.**.mapper")
public class MapperConfig {
}
