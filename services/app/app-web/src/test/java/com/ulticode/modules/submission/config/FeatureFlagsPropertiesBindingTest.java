package com.ulticode.modules.submission.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0-2: proves the Spring Boot Binder actually maps the nested YAML key
 * {@code app.features.judge-queue.use-port} onto the new
 * {@link FeatureFlagsProperties.JudgeQueue} inner class.
 *
 * <p>Pre-P0-2 the same YAML was silently dropped (flat field
 * {@code judgeQueueUsePort} never received the kebab-cased nested path), so
 * the M3c cutover flag was stuck at {@code false}. This test guards the
 * binding contract end-to-end through the real Binder (the same code path
 * Spring Boot uses at startup), not just reflection on the properties class.
 */
@DisplayName("P0-2 FeatureFlagsProperties nested judge-queue binding")
class FeatureFlagsPropertiesBindingTest {

    private FeatureFlagsProperties bind(String yaml) throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(
                "test-yaml", new ByteArrayResource(yaml.getBytes()));
        StandardEnvironment env = new StandardEnvironment();
        MutablePropertySources sources_ = env.getPropertySources();
        sources.forEach(sources_::addLast);
        return Binder.get(env).bindOrCreate("app.features", FeatureFlagsProperties.class);
    }

    @Test
    @DisplayName("nested judge-queue.use-port binds to JudgeQueue.usePort (was silently dropped pre-P0-2)")
    void usePortBindsFromNestedYaml() throws Exception {
        String yaml = "app:\n  features:\n    judge-queue:\n      use-port: true\n      envelope-version: 2\n";
        FeatureFlagsProperties props = bind(yaml);

        assertThat(props.getJudgeQueue()).as("nested JudgeQueue bean must be bound").isNotNull();
        assertThat(props.getJudgeQueue().isUsePort())
                .as("nested use-port=true must reach the inner class (was stuck false pre-P0-2)")
                .isTrue();
        assertThat(props.getJudgeQueue().getEnvelopeVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("default values when YAML omits judge-queue (CI features-off profile)")
    void defaultsWhenOmitted() throws Exception {
        FeatureFlagsProperties props = bind("app:\n  features:\n    use-judge-outbox: false\n");
        assertThat(props.getJudgeQueue().isUsePort()).isFalse();
        assertThat(props.getJudgeQueue().getEnvelopeVersion()).isEqualTo(1);
        assertThat(props.getJudgeQueue().getCutoverAt()).isNull();
    }

    @Test
    @DisplayName("cutover-at (ISO-8601) binds to LocalDateTime (previously dropped by Binder)")
    void cutoverAtBinds() throws Exception {
        String yaml = "app:\n  features:\n    judge-queue:\n      cutover-at: \"2026-06-14T13:00:00\"\n";
        FeatureFlagsProperties props = bind(yaml);
        assertThat(props.getJudgeQueue().getCutoverAt())
                .isEqualTo(LocalDateTime.of(2026, 6, 14, 13, 0, 0));
    }
}
