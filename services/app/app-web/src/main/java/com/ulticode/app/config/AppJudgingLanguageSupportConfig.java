package com.ulticode.app.config;

import com.ulticode.app.api.service.JudgingLanguageSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * Provides the {@link JudgingLanguageSupport} port for backend-app.
 *
 * <p>Backend-app's {@code DefaultProblemProjection} needs the language catalog
 * to shape problem VOs. The canonical implementation lives in
 * {@code backend-judge-runtime} (which backend-app does not depend on at
 * runtime), so backend-app owns a trivial copy of the constants here. Both
 * copies serve the same app-api contract and must stay in sync.
 */
@Configuration
public class AppJudgingLanguageSupportConfig {

    private static final Set<String> ADVERTISED = Set.of(
            "javascript", "python", "java", "c", "cpp");

    private static final Set<String> EXECUTABLE = Set.of("java", "python", "cpp");

    @Bean
    public JudgingLanguageSupport judgingLanguageSupport() {
        return new JudgingLanguageSupport() {
            @Override
            public Set<String> advertisedLanguages() {
                return ADVERTISED;
            }

            @Override
            public Set<String> executableLanguages() {
                return EXECUTABLE;
            }

            @Override
            public Set<String> supportedLanguages() {
                return EXECUTABLE;
            }
        };
    }
}
