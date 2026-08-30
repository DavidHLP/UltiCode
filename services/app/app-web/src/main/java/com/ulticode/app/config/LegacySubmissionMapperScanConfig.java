package com.ulticode.app.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Restores the removed App Submission mapper only for explicit rollback mode. */
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'${app.runtime.mode:dev-lite}' == 'legacy-rollback'")
@MapperScan(value = "com.ulticode.modules.submission.mapper", annotationClass = Mapper.class)
public class LegacySubmissionMapperScanConfig {
}
