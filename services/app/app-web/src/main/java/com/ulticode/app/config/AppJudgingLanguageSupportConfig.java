package com.ulticode.app.config;

import com.ulticode.modules.problem.port.ProblemLanguageCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/** Composition root for the App-private advertised language catalog. */
@Configuration
public class AppJudgingLanguageSupportConfig {

    private static final Set<String> ADVERTISED = Set.of(
            "javascript", "python", "java", "c", "cpp");

    @Bean
    public ProblemLanguageCatalog problemLanguageCatalog() {
        return () -> ADVERTISED;
    }
}
