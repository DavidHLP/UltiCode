package com.ulticode.app.i18n.service;

import com.ulticode.app.i18n.constants.I18nConstants;
import com.ulticode.app.i18n.dto.BulkUpsertDTO;

import java.util.List;
import java.util.Map;

/**
 * Translation catalog service.
 *
 * <p>Owns validated lookup and administrative writes for the translation
 * catalog. Entity-type, locale, and translatable-field validation live here
 * so the catalog is self-protecting regardless of the caller.
 */
public interface I18nService {

    /**
     * Get all translations for a specific entity in a given locale.
     *
     * @param entityType the entity type (must be a valid {@link I18nConstants.TranslatableEntity})
     * @param entityId   the entity ID
     * @param locale     the locale code (must be a supported locale)
     * @return map of field name to translated content
     * @throws com.ulticode.common.exception.BusinessException if the entity
     *         type or locale is invalid
     */
    Map<String, String> getTranslations(String entityType, String entityId, String locale);

    /**
     * Bulk upsert translations.
     *
     * <p>Each item is validated (entity type, locale, translatable field)
     * before any persistence; the first invalid item aborts the batch.
     *
     * @param translations  list of translation items
     * @param skipDuplicates whether to skip duplicates instead of updating
     * @param actorId       the actor performing the write
     * @return the result with counts of created, updated, and skipped
     */
    BulkUpsertDTO bulkUpsertTranslations(List<BulkUpsertDTO.TranslationItem> translations,
                                         boolean skipDuplicates, String actorId);
}
