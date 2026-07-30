package com.ulticode.admin.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Registers only MyBatis mapper interfaces owned by backend-admin modules.
 */
@Configuration
@MapperScan("com.ulticode.modules.**.mapper")
public class MapperConfig {
}
