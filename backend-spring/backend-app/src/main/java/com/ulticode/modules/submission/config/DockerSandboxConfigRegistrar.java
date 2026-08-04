package com.ulticode.modules.submission.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the record-typed {@link DockerSandboxConfig} properties bean
 * (P7-RELOCATE-SUBMISSION-001).
 *
 * <p>The legacy monolith boot class carried {@code @ConfigurationPropertiesScan},
 * which picked this record up implicitly. The split boot classes dropped that
 * annotation, and a record cannot self-register as a {@code @Configuration}
 * class, so the binding registration lives here explicitly. Placed in the
 * submission module (not the app shell) so both the app and admin contexts
 * pick it up through their {@code com.ulticode} component scans.
 */
@Configuration
@EnableConfigurationProperties(DockerSandboxConfig.class)
public class DockerSandboxConfigRegistrar {
}
