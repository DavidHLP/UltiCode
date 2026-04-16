package com.ulticode.common.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis mapper scanning configuration.
 *
 * <p>Moved from UlticodeBackendApplication to a dedicated @Configuration class
 * so that @WebMvcTest slice tests can exclude it via excludeFilters,
 * avoiding the need for a full DataSource/MyBatis setup in controller tests.</p>
 */
@Configuration
@MapperScan("com.ulticode.modules.*.mapper")
public class MapperConfig {
}
