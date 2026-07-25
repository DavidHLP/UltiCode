package com.ulticode.modules.i18n.constants;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Constants for internationalization/translation module.
 */
public final class I18nConstants {

    private I18nConstants() {
        // Prevent instantiation
    }

    /**
     * Set of supported locales.
     */
    public static final Set<String> SUPPORTED_LOCALES = Set.of(
            "en-US",
            "zh-CN",
            "zh-TW",
            "ja-JP"
    );

    /**
     * Enum representing translatable entity types.
     */
    public enum TranslatableEntity {
        PROBLEM,
        PROBLEM_DETAIL,
        CONTEST,
        SOLUTION,
        POST
    }

    /**
     * Map of translatable fields for each entity type.
     */
    public static final Map<TranslatableEntity, List<String>> TRANSLATABLE_FIELDS = Map.of(
            TranslatableEntity.PROBLEM, List.of("title", "summary"),
            TranslatableEntity.PROBLEM_DETAIL, List.of("description", "hints", "solution"),
            TranslatableEntity.CONTEST, List.of("title", "description"),
            TranslatableEntity.SOLUTION, List.of("title", "content"),
            TranslatableEntity.POST, List.of("title", "content")
    );

    /**
     * Check if a field is translatable for a given entity type.
     *
     * @param entityType the entity type
     * @param fieldName  the field name to check
     * @return true if the field is translatable
     */
    public static boolean isTranslatableField(TranslatableEntity entityType, String fieldName) {
        List<String> fields = TRANSLATABLE_FIELDS.get(entityType);
        return fields != null && fields.contains(fieldName);
    }

    /**
     * Check if a locale is supported.
     *
     * @param locale the locale to check
     * @return true if the locale is supported
     */
    public static boolean isSupportedLocale(String locale) {
        return locale != null && SUPPORTED_LOCALES.contains(locale);
    }
}
