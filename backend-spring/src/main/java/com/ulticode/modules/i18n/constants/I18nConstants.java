package com.ulticode.modules.i18n.constants;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Constants for internationalization/translation module.
 */
public final class I18nConstants {

    private I18nConstants() {
        // Prevent instantiation
    }

    /**
     * Default fallback locale when requested locale is not available.
     */
    public static final String FALLBACK_LOCALE = "en-US";

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
     * Pattern for parsing Accept-Language header.
     * Matches language tags like "en-US", "zh-CN", with optional quality values.
     */
    private static final Pattern LOCALE_PATTERN = Pattern.compile(
            "([a-zA-Z]{2,3}(?:-[a-zA-Z]{2,4})?)(?:;q=([0-9.]+))?"
    );

    /**
     * Parse Accept-Language header and return the best matching supported locale.
     * <p>
     * The Accept-Language header format is defined in RFC 7231.
     * Example: "en-US,en;q=0.9,zh-CN;q=0.8"
     *
     * @param header the Accept-Language header value
     * @return the best matching supported locale, or fallback locale if no match
     */
    public static String parseAcceptLanguage(String header) {
        if (header == null || header.isBlank()) {
            return FALLBACK_LOCALE;
        }

        // Parse locales with their quality values
        List<LocaleQuality> locales = new java.util.ArrayList<>();
        String[] parts = header.split(",");

        for (String part : parts) {
            Matcher matcher = LOCALE_PATTERN.matcher(part.trim());
            if (matcher.matches()) {
                String locale = normalizeLocale(matcher.group(1));
                double quality = 1.0;
                if (matcher.group(2) != null) {
                    try {
                        quality = Double.parseDouble(matcher.group(2));
                    } catch (NumberFormatException e) {
                        quality = 1.0;
                    }
                }
                locales.add(new LocaleQuality(locale, quality));
            }
        }

        // Sort by quality (descending)
        locales.sort((a, b) -> Double.compare(b.quality, a.quality));

        // Find first matching supported locale
        for (LocaleQuality lq : locales) {
            // Try exact match first
            if (SUPPORTED_LOCALES.contains(lq.locale)) {
                return lq.locale;
            }
            // Try language-only match (e.g., "en" matches "en-US")
            String languageOnly = lq.locale.split("-")[0];
            for (String supported : SUPPORTED_LOCALES) {
                if (supported.startsWith(languageOnly + "-") || supported.equals(languageOnly)) {
                    return supported;
                }
            }
        }

        return FALLBACK_LOCALE;
    }

    /**
     * Normalize a locale string to standard format (e.g., "en-us" -> "en-US").
     *
     * @param locale the locale string to normalize
     * @return the normalized locale string
     */
    private static String normalizeLocale(String locale) {
        if (locale == null || locale.isEmpty()) {
            return FALLBACK_LOCALE;
        }

        String[] parts = locale.split("-");
        if (parts.length == 1) {
            return parts[0].toLowerCase();
        }

        // Format: language-REGION (e.g., en-US, zh-CN)
        return parts[0].toLowerCase() + "-" + parts[1].toUpperCase();
    }

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

    /**
     * Helper class to hold locale with quality value.
     */
    private static class LocaleQuality {
        final String locale;
        final double quality;

        LocaleQuality(String locale, double quality) {
            this.locale = locale;
            this.quality = quality;
        }
    }
}
