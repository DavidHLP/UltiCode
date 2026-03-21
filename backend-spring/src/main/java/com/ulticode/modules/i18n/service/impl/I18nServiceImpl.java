package com.ulticode.modules.i18n.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.i18n.constants.I18nConstants;
import com.ulticode.modules.i18n.dto.BulkUpsertDTO;
import com.ulticode.modules.i18n.entity.Translation;
import com.ulticode.modules.i18n.mapper.TranslationMapper;
import com.ulticode.modules.i18n.service.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of I18nService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class I18nServiceImpl implements I18nService {

    private final TranslationMapper translationMapper;

    @Override
    public Map<String, String> getTranslations(I18nConstants.TranslatableEntity entityType, String entityId, String locale) {
        if (entityId == null || entityId.isBlank() || locale == null || locale.isBlank()) {
            return Collections.emptyMap();
        }

        List<Translation> translations = translationMapper.findByEntityAndLocale(
                entityType.name(),
                entityId,
                locale
        );

        return translations.stream()
                .collect(Collectors.toMap(
                        Translation::getFieldName,
                        Translation::getContent,
                        (existing, replacement) -> replacement // In case of duplicates, use the last one
                ));
    }

    @Override
    public Map<String, Map<String, String>> getBatchTranslations(
            I18nConstants.TranslatableEntity entityType,
            List<String> entityIds,
            String locale) {

        if (entityIds == null || entityIds.isEmpty() || locale == null || locale.isBlank()) {
            return Collections.emptyMap();
        }

        // Use parameterized query with List<String> (safe from SQL injection)
        List<Translation> translations = translationMapper.findByEntitiesAndLocale(
                entityType.name(),
                entityIds,
                locale
        );

        // Group by entity ID
        return translations.stream()
                .collect(Collectors.groupingBy(
                        Translation::getEntityId,
                        Collectors.toMap(
                                Translation::getFieldName,
                                Translation::getContent,
                                (existing, replacement) -> replacement
                        )
                ));
    }

    @Override
    public <T> T applyTranslations(T entity, Map<String, String> translations, List<String> fields) {
        if (entity == null || translations == null || translations.isEmpty()) {
            return entity;
        }

        Class<?> clazz = entity.getClass();
        for (String fieldName : fields) {
            String translatedValue = translations.get(fieldName);
            if (translatedValue != null) {
                try {
                    Field field = findField(clazz, fieldName);
                    if (field != null) {
                        field.setAccessible(true);
                        field.set(entity, translatedValue);
                    }
                } catch (Exception e) {
                    log.debug("Could not apply translation to field '{}': {}", fieldName, e.getMessage());
                }
            }
        }

        return entity;
    }

    /**
     * Find a field in the class hierarchy by name.
     *
     * @param clazz     the class to search
     * @param fieldName the field name to find
     * @return the Field object or null if not found
     */
    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Map<String, Object>> List<T> translateEntities(
            I18nConstants.TranslatableEntity entityType,
            List<T> entities,
            String locale) {

        if (entities == null || entities.isEmpty() || locale == null || locale.isBlank()) {
            return entities != null ? entities : Collections.emptyList();
        }

        // Get list of translatable fields for this entity type
        List<String> translatableFields = I18nConstants.TRANSLATABLE_FIELDS.get(entityType);
        if (translatableFields == null || translatableFields.isEmpty()) {
            return entities;
        }

        // Collect all entity IDs
        List<String> entityIds = entities.stream()
                .map(e -> (String) e.get("id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (entityIds.isEmpty()) {
            return entities;
        }

        // Get batch translations
        Map<String, Map<String, String>> batchTranslations = getBatchTranslations(entityType, entityIds, locale);

        // Apply translations to each entity
        for (T entity : entities) {
            String entityId = (String) entity.get("id");
            if (entityId != null) {
                Map<String, String> translations = batchTranslations.get(entityId);
                if (translations != null) {
                    for (String field : translatableFields) {
                        String translatedValue = translations.get(field);
                        if (translatedValue != null) {
                            entity.put(field, translatedValue);
                        }
                    }
                }
            }
        }

        return entities;
    }

    @Override
    @Transactional
    public BulkUpsertDTO bulkUpsertTranslations(List<BulkUpsertDTO.TranslationItem> translations, boolean skipDuplicates) {
        BulkUpsertDTO result = new BulkUpsertDTO();
        result.setSkipDuplicates(skipDuplicates);

        if (translations == null || translations.isEmpty()) {
            return result;
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;

        // Group translations by entity type for batch querying
        Map<String, List<BulkUpsertDTO.TranslationItem>> byEntityType = translations.stream()
                .collect(Collectors.groupingBy(BulkUpsertDTO.TranslationItem::getEntityType));

        // Maps to hold items that need to be created vs updated
        List<Translation> toCreate = new ArrayList<>();
        List<Translation> toUpdate = new ArrayList<>();

        for (Map.Entry<String, List<BulkUpsertDTO.TranslationItem>> entry : byEntityType.entrySet()) {
            String entityType = entry.getKey();
            List<BulkUpsertDTO.TranslationItem> items = entry.getValue();

            // Collect all unique (entityId, fieldName, locale) combinations for batch query
            Set<String> entityIds = items.stream()
                    .map(BulkUpsertDTO.TranslationItem::getEntityId)
                    .collect(Collectors.toSet());

            Set<String> locales = items.stream()
                    .map(BulkUpsertDTO.TranslationItem::getLocale)
                    .collect(Collectors.toSet());

            // Batch fetch existing translations for this entity type
            LambdaQueryWrapper<Translation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Translation::getEntityType, entityType)
                    .in(Translation::getEntityId, entityIds)
                    .in(Translation::getLocale, locales);

            List<Translation> existingTranslations = translationMapper.selectList(queryWrapper);

            // Create lookup key: "entityId:fieldName:locale"
            Map<String, Translation> existingMap = existingTranslations.stream()
                    .collect(Collectors.toMap(
                            t -> t.getEntityId() + ":" + t.getFieldName() + ":" + t.getLocale(),
                            t -> t,
                            (a, b) -> a
                    ));

            // Process each item
            for (BulkUpsertDTO.TranslationItem item : items) {
                String key = item.getEntityId() + ":" + item.getFieldName() + ":" + item.getLocale();
                Translation existing = existingMap.get(key);

                if (existing != null) {
                    if (skipDuplicates) {
                        skipped++;
                    } else {
                        // Mark for update
                        existing.setContent(item.getContent());
                        if (item.getCreatedBy() != null) {
                            existing.setUpdatedBy(item.getCreatedBy());
                        }
                        toUpdate.add(existing);
                        updated++;
                    }
                } else {
                    // Create new translation
                    Translation newTranslation = new Translation();
                    newTranslation.setEntityType(item.getEntityType());
                    newTranslation.setEntityId(item.getEntityId());
                    newTranslation.setFieldName(item.getFieldName());
                    newTranslation.setLocale(item.getLocale());
                    newTranslation.setContent(item.getContent());
                    newTranslation.setCreatedBy(item.getCreatedBy());
                    newTranslation.setUpdatedBy(item.getCreatedBy());
                    toCreate.add(newTranslation);
                    created++;
                }
            }
        }

        // Batch insert new translations
        for (Translation translation : toCreate) {
            translationMapper.insert(translation);
        }

        // Batch update existing translations
        for (Translation translation : toUpdate) {
            translationMapper.updateById(translation);
        }

        result.setCreated(created);
        result.setUpdated(updated);
        result.setSkipped(skipped);

        log.info("Bulk upsert completed: {} created, {} updated, {} skipped", created, updated, skipped);

        return result;
    }

    @Override
    public String parseAcceptLanguage(String header) {
        return I18nConstants.parseAcceptLanguage(header);
    }
}
