package com.ulticode.common.config;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Global Jackson ObjectMapper configuration.
 *
 * <p>Registers {@link JavaTimeModule} so that {@code java.time.LocalDateTime} and friends
 * are serializable by ad-hoc {@code ObjectMapper} instances created by framework
 * integrations (notably MyBatis-Plus {@code JacksonTypeHandler}, which calls
 * {@code new ObjectMapper().writeValueAsString(...)} on JSON columns). Without this,
 * audit-log writes that include {@code LocalDateTime} (e.g. {@code audit_logs.new_values}
 * from {@code AdminForumServiceImpl.deletePost}) fail with
 * {@code InvalidDefinitionException} and roll back the entire transaction, surfacing as
 * a generic HTTP 500 to the client.</p>
 *
 * <p>Also pins a few project-wide serialization conventions:</p>
 * <ul>
 *   <li>Disable {@code WRITE_DATES_AS_TIMESTAMPS} so dates render as ISO-8601 strings.</li>
 *   <li>Drop null fields in JSON output to keep payloads small and consistent with
 *       {@code @JsonInclude(NON_NULL)} on the VOs.</li>
 *   <li>Tolerate unknown JSON properties on input (forward-compatibility for client
 *       evolution without server redeploys).</li>
 * </ul>
 *
 * <p>Also hands the resulting {@link ObjectMapper} to MyBatis-Plus
 * {@link JacksonTypeHandler} via its public static {@code setObjectMapper} hook
 * (3.5.16+). Without that, JSON column writes (e.g. {@code audit_logs.new_values}
 * containing {@code LocalDateTime}) blow up with {@code InvalidDefinitionException}
 * and roll back the whole transaction — see
 * docs/forum-api-curl-test-report-2026-06-08.md §3.</p>
 */
@Slf4j
@Configuration
public class JacksonConfig {

    /**
     * Primary ObjectMapper used by Spring MVC and any injection point that needs
     * the canonical project-wide serialization shape.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new ParameterNamesModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    /**
     * Builder customizer so that any Spring-MVC-built ObjectMapper (e.g. those
     * wired into HttpMessageConverters via Jackson2ObjectMapperBuilder) also picks
     * up the JavaTimeModule + NON_NULL inclusion. The two beans above are
     * belt-and-suspenders: this one covers message converters, the @Primary one
     * covers direct @Autowired usage.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .modulesToInstall(new JavaTimeModule(), new ParameterNamesModule())
                .featuresToDisable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        SerializationFeature.FAIL_ON_EMPTY_BEANS,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .serializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Hand the Spring-managed {@link ObjectMapper} to MyBatis-Plus
     * {@link JacksonTypeHandler} so that JSON column writes/reads use the same
     * project-wide serialization shape (including {@code JavaTimeModule}). Runs
     * once all singletons are ready, so we are guaranteed to pick up the
     * {@link #objectMapper()} bean (not some auto-configured placeholder).
     */
    @Bean
    public SmartInitializingSingleton mybatisPlusObjectMapperInstaller(ObjectMapper objectMapper) {
        return () -> {
            JacksonTypeHandler.setObjectMapper(objectMapper);
            // DEBUG, not INFO: this runs on every startup, and the Spring boot
            // banner already records enough. Operators can crank to DEBUG when
            // diagnosing a JSR-310-shaped serialization failure.
            log.debug("Installed Spring-managed ObjectMapper into MyBatis-Plus JacksonTypeHandler "
                    + "(LocalDateTime serializable={})", objectMapper.canSerialize(java.time.LocalDateTime.class));
        };
    }
}
