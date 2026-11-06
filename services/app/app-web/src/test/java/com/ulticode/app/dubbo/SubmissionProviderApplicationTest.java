package com.ulticode.app.dubbo;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.spring.util.AnnotationUtils;
import org.apache.dubbo.metadata.ServiceNameMapping;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** The owner is explicit; provider addresses still come from normal instance discovery. */
class SubmissionProviderApplicationTest {
    private static final String PROPERTY = "ulticode.rpc.providers.submission";
    private static final String DECLARATION = "${" + PROPERTY + ":backend-submission}";

    @Test
    void allSubmissionReferencesDeclareOwnerWithoutRetargetingOtherReferences() throws Exception {
        int submissionReferences = 0;
        for (Field field : referenceFields()) {
            DubboReference reference = field.getAnnotation(DubboReference.class);
            if (reference.group().equals("backend-submission")) {
                submissionReferences++;
                assertThat(reference.providedBy()).as(field.toString()).containsExactly(DECLARATION);
            } else {
                assertThat(reference.providedBy()).as(field.toString()).isEmpty();
            }
        }
        assertThat(submissionReferences).isGreaterThanOrEqualTo(8);
    }

    @Test
    void actualDubboAnnotationResolutionUsesDefaultAndConfiguredProviderName() throws Exception {
        assertMapping(cleanEnvironment(), "backend-submission");
        StandardEnvironment environment = cleanEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("override", Map.of(
                PROPERTY, "custom-submission", "DUBBO_APPLICATION_NAME", "different-consumer-name")));
        assertMapping(environment, "custom-submission");
    }

    @Test
    void environmentAliasResolvesWithoutUsingConsumersOwnApplicationName() throws Exception {
        StandardEnvironment environment = cleanEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("fixture", Map.of(
                "ULTICODE_RPC_PROVIDERS_SUBMISSION", "renamed-submission",
                "DUBBO_APPLICATION_NAME", "renamed-consumer")));
        assertMapping(environment, "renamed-submission");
    }

    private static void assertMapping(StandardEnvironment environment, String application) throws Exception {
        int checked = 0;
        for (Field field : referenceFields()) {
            DubboReference reference = field.getAnnotation(DubboReference.class);
            if (!reference.group().equals("backend-submission")) continue;
            String[] providedBy = (String[]) AnnotationUtils.getAttributes(reference, environment, true).get("providedBy");
            assertThat(providedBy).as(field.toString()).containsExactly(application);
            URL consumer = URL.valueOf("consumer://localhost/" + field.getType().getName())
                    .addParameter("provided-by", String.join(",", providedBy));
            assertThat(ServiceNameMapping.getMappingByUrl(consumer)).containsExactly(application);
            checked++;
        }
        assertThat(checked).isGreaterThanOrEqualTo(8);
    }

    private static StandardEnvironment cleanEnvironment() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        return environment;
    }

    private static List<Field> referenceFields() throws Exception {
        Path root = Path.of("target/classes");
        List<Field> references = new ArrayList<>();
        try (var classes = Files.walk(root.resolve("com/ulticode"))) {
            for (Path path : classes.filter(p -> p.toString().endsWith(".class")).toList()) {
                String name = root.relativize(path).toString().replace(java.io.File.separatorChar, '.');
                Class<?> type = Class.forName(name.substring(0, name.length() - 6), false,
                        SubmissionProviderApplicationTest.class.getClassLoader());
                for (Field field : type.getDeclaredFields()) {
                    if (field.isAnnotationPresent(DubboReference.class)) references.add(field);
                }
            }
        }
        return references;
    }
}
