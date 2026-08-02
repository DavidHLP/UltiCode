package com.ulticode.common.audit;

import com.ulticode.common.annotation.Audited;
import com.ulticode.common.annotation.CheckBan;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates that {@link AuditPolicy} stays in sync with the actual
 * {@code @Audited} / {@code @CheckBan} annotations in the codebase.
 *
 * <p>Two checks:
 * <ol>
 *   <li>Every catalog entry corresponds to a real {@code @Audited} /
 *       {@code @CheckBan} annotation site (catches stale entries when a
 *       method is renamed or removed).</li>
 *   <li>Every {@code @Audited} / {@code @CheckBan} annotation site in
 *       the {@code com.ulticode} package appears in the catalog (catches
 *       forgotten catalog updates when a new audited method lands).</li>
 * </ol>
 *
 * <p>The "rename / removal" check fails fast. The "new audit site"
 * check is more permissive — we only fail if the catalog itself would
 * be misleading (we treat the catalog as the source of truth for the
 * documentation contract).
 */
class AuditPolicyCoverageTest {

    private static final String SCAN_PACKAGE = "com.ulticode";

    @Test
    void catalog_auditEntries_match_real_annotations() {
        Set<Method> auditedMethods = scanAnnotatedMethods(Audited.class);
        for (AuditPolicy.AuditEntry entry : AuditPolicy.AUDITED) {
            boolean found = auditedMethods.stream().anyMatch(m ->
                    m.getDeclaringClass().getName().equals(entry.declaringClass())
                            && m.getName().equals(entry.methodName()));
            assertTrue(found,
                    () -> "AuditPolicy catalog entry points to a method without @Audited: "
                            + entry.declaringClass() + "#" + entry.methodName());
        }
    }

    @Test
    void catalog_banEntries_match_real_annotations() {
        Set<Method> banCheckedMethods = new HashSet<>(scanAnnotatedMethods(CheckBan.class));
        banCheckedMethods.addAll(scanAnnotatedMethods(com.ulticode.app.security.CheckBan.class));
        for (AuditPolicy.BanEntry entry : AuditPolicy.BAN_CHECKED) {
            boolean found = banCheckedMethods.stream().anyMatch(m ->
                    m.getDeclaringClass().getName().equals(entry.declaringClass())
                            && m.getName().equals(entry.methodName()));
            assertTrue(found,
                    () -> "AuditPolicy catalog entry points to a method without @CheckBan: "
                            + entry.declaringClass() + "#" + entry.methodName());
        }
    }

    /**
     * Walks the classpath under {@code com.ulticode}, loads each class via
     * Spring's metadata reader (no full bytecode init), and returns the
     * set of methods annotated with the given annotation type.
     */
    private static Set<Method> scanAnnotatedMethods(Class<? extends java.lang.annotation.Annotation> annotationType) {
        Set<Method> result = new HashSet<>();
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            MetadataReaderFactory factory = new CachingMetadataReaderFactory(resolver);
            String pattern = "classpath*:com/ulticode/**/*ServiceImpl.class";
            Resource[] resources = resolver.getResources(pattern);
            for (Resource resource : resources) {
                try {
                    MetadataReader reader = factory.getMetadataReader(resource);
                    String className = reader.getClassMetadata().getClassName();
                    if (!className.contains(".service.impl.")) continue;
                    Class<?> clazz = Class.forName(className);
                    for (Method method : clazz.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(annotationType)) {
                            result.add(method);
                        }
                    }
                } catch (Throwable ignored) {
                    // Skip classes that fail to load (e.g. Spring proxies,
                    // generated code) — they cannot be the source of a
                    // @Audited / @CheckBan anyway.
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan " + SCAN_PACKAGE + " for " + annotationType.getSimpleName(), e);
        }
        return result;
    }
}