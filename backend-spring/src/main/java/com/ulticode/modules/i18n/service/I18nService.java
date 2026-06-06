package com.ulticode.modules.i18n.service;

import com.ulticode.modules.i18n.constants.I18nConstants;
import com.ulticode.modules.i18n.dto.BulkUpsertDTO;

import java.util.List;
import java.util.Map;

/**
 * Service interface for internationalization operations.
 */
public interface I18nService {

    /**
     * Get all translations for a specific entity in a given locale.
     *
     * @param entityType the type of entity
     * @param entityId   the entity ID
     * @param locale     the locale code
     * @return map of field name to translated content
     */
    Map<String, String> getTranslations(I18nConstants.TranslatableEntity entityType, String entityId, String locale);

    /**
     * Get translations for multiple entities at once.
     *
     * @param entityType the type of entity
     * @param entityIds  list of entity IDs
     * @param locale     the locale code
     * @return map of entity ID to field translations
     */
    Map<String, Map<String, String>> getBatchTranslations(
            I18nConstants.TranslatableEntity entityType,
            List<String> entityIds,
            String locale
    );

    /**
     * Apply translations to an entity object using reflection.
     *
     * @param entity       the entity to translate
     * @param translations map of field name to translated content
     * @param fields       list of fields to apply translations to
     * @param <T>          the entity type
     * @return the translated entity
     */
    <T> T applyTranslations(T entity, Map<String, String> translations, List<String> fields);

    /**
     * Apply translations to a list of map-based entities.
     *
     * @param entityType the type of entity
     * @param entities   list of entity maps
     * @param locale     the locale code
     * @param <T>        the map type
     * @return list of translated entity maps
     */
    <T extends Map<String, Object>> List<T> translateEntities(
            I18nConstants.TranslatableEntity entityType,
            List<T> entities,
            String locale
    );

    /**
     * Bulk upsert translations.
     *
     * @param translations  list of translation items
     * @param skipDuplicates whether to skip duplicates instead of updating
     * @return the result with counts of created, updated, and skipped
     */
    BulkUpsertDTO bulkUpsertTranslations(List<BulkUpsertDTO.TranslationItem> translations,
                                         boolean skipDuplicates, String actorId);

    /**
     * Parse Accept-Language header and return the best matching locale.
     *
     * @param header the Accept-Language header value
     * @return the matched locale
     */
    String parseAcceptLanguage(String header);
}
