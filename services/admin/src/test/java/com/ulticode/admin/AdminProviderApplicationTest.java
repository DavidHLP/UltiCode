package com.ulticode.admin;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.spring.util.AnnotationUtils;
import org.apache.dubbo.metadata.ServiceNameMapping;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.junit.jupiter.api.Assertions.*;

class AdminProviderApplicationTest {
    @Test
    void everyOwnerReferenceResolvesItsExplicitApplicationWithoutMappingLookup() throws Exception {
        int app = 0;
        int submission = 0;
        int other = 0;
        Path sources = Path.of("src/main/java");
        try (var files = Files.walk(sources)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String className = sources.relativize(file).toString()
                        .replace('/', '.').replace('\\', '.').replaceAll("\\.java$", "");
                Class<?> type = Class.forName(className, false, getClass().getClassLoader());
                for (Field field : type.getDeclaredFields()) {
                    DubboReference reference = field.getAnnotation(DubboReference.class);
                    if (reference == null) {
                        continue;
                    }
                    String owner = switch (reference.group()) {
                        case "backend-app" -> "app";
                        case "backend-submission" -> "submission";
                        default -> null;
                    };
                    if (owner == null) {
                        assertArrayEquals(new String[0], reference.providedBy(), field.toString());
                        other++;
                        continue;
                    }
                    if (owner.equals("app")) app++; else submission++;
                    String property = "ulticode.rpc.providers." + owner;
                    assertArrayEquals(new String[]{"${" + property + ":backend-" + owner + "}"},
                            reference.providedBy(), field.toString());
                    StandardEnvironment defaults = isolatedEnvironment();
                    assertMapping(reference, defaults, "backend-" + owner);
                    defaults.getPropertySources().addFirst(new MapPropertySource("override",
                            Map.of(property, "custom-" + owner)));
                    assertMapping(reference, defaults, "custom-" + owner);
                    StandardEnvironment environmentAlias = isolatedEnvironment();
                    environmentAlias.getPropertySources().addFirst(new SystemEnvironmentPropertySource("alias",
                            Map.of("ULTICODE_RPC_PROVIDERS_" + owner.toUpperCase(java.util.Locale.ROOT),
                                    "env-" + owner)));
                    assertMapping(reference, environmentAlias, "env-" + owner);
                }
            }
        }
        assertEquals(40, app + submission, "All Admin App and Submission references must be covered");
        assertTrue(app > 0);
        assertTrue(submission > 0);
        assertTrue(other > 0, "Other owner groups remain untargeted");
    }

    private StandardEnvironment isolatedEnvironment() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        return environment;
    }

    private void assertMapping(DubboReference reference, StandardEnvironment environment, String expected) {
        Map<String, Object> attributes = AnnotationUtils.getAttributes(reference, environment, false);
        String[] providedBy = (String[]) attributes.get("providedBy");
        assertArrayEquals(new String[]{expected}, providedBy);
        URL url = new URL("consumer", "127.0.0.1", 0).addParameter("provided-by", String.join(",", providedBy));
        assertEquals(Set.of(expected), ServiceNameMapping.getMappingByUrl(url));
    }
}
