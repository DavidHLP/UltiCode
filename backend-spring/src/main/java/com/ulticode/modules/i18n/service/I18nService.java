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
