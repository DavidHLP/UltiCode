package com.ulticode.app.i18n.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.app.i18n.constants.I18nConstants;
import com.ulticode.app.i18n.dto.BulkUpsertDTO;
import com.ulticode.app.i18n.entity.Translation;
import com.ulticode.app.i18n.mapper.TranslationMapper;
import com.ulticode.app.i18n.service.I18nService;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of {@link I18nService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class I18nServiceImpl implements I18nService {

    private final TranslationMapper translationMapper;

    @Override
    public Map<String, String> getTranslations(String entityType, String entityId, String locale) {
        I18nConstants.TranslatableEntity type = requireEntityType(entityType);
        requireLocale(locale);

        if (entityId == null || entityId.isBlank()) {
            return Collections.emptyMap();
        }

        List<Translation> translations = translationMapper.findByEntityAndLocale(
                type.name(),
                entityId,
                locale
        );

        return translations.stream()
                .collect(Collectors.toMap(
                        Translation::getFieldName,
                        Translation::getContent,
                        (existing, replacement) -> replacement
                ));
    }

    @Override
    @Transactional
    public BulkUpsertDTO bulkUpsertTranslations(List<BulkUpsertDTO.TranslationItem> translations,
                                                boolean skipDuplicates, String actorId) {
        BulkUpsertDTO result = new BulkUpsertDTO();
        result.setSkipDuplicates(skipDuplicates);

        if (translations == null || translations.isEmpty()) {
            return result;
        }

        // Validate every item up front so a partial batch never persists.
        for (BulkUpsertDTO.TranslationItem item : translations) {
            I18nConstants.TranslatableEntity entityType = requireEntityType(item.getEntityType());
            requireLocale(item.getLocale());
            if (!I18nConstants.isTranslatableField(entityType, item.getFieldName())) {
                throw new BusinessException(BaseErrorCode.VALIDATION_FAILED,
                        "Invalid field name for entity type: " + item.getFieldName());
            }
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;

        Map<String, List<BulkUpsertDTO.TranslationItem>> byEntityType = translations.stream()
                .collect(Collectors.groupingBy(BulkUpsertDTO.TranslationItem::getEntityType));

        List<Translation> toCreate = new ArrayList<>();
        List<Translation> toUpdate = new ArrayList<>();

        for (Map.Entry<String, List<BulkUpsertDTO.TranslationItem>> entry : byEntityType.entrySet()) {
            String entityType = entry.getKey();
            List<BulkUpsertDTO.TranslationItem> items = entry.getValue();

            Set<String> entityIds = items.stream()
                    .map(BulkUpsertDTO.TranslationItem::getEntityId)
                    .collect(Collectors.toSet());

            Set<String> locales = items.stream()
                    .map(BulkUpsertDTO.TranslationItem::getLocale)
                    .collect(Collectors.toSet());

            LambdaQueryWrapper<Translation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Translation::getEntityType, entityType)
                    .in(Translation::getEntityId, entityIds)
                    .in(Translation::getLocale, locales);

            List<Translation> existingTranslations = translationMapper.selectList(queryWrapper);

            Map<String, Translation> existingMap = existingTranslations.stream()
                    .collect(Collectors.toMap(
                            t -> t.getEntityId() + ":" + t.getFieldName() + ":" + t.getLocale(),
                            t -> t,
                            (a, b) -> a
                    ));

            for (BulkUpsertDTO.TranslationItem item : items) {
                String key = item.getEntityId() + ":" + item.getFieldName() + ":" + item.getLocale();
                Translation existing = existingMap.get(key);

                if (existing != null) {
                    if (skipDuplicates) {
                        skipped++;
                    } else {
                        existing.setContent(item.getContent());
                        existing.setUpdatedBy(actorId);
                        toUpdate.add(existing);
                        updated++;
                    }
                } else {
                    Translation newTranslation = new Translation();
                    newTranslation.setEntityType(item.getEntityType());
                    newTranslation.setEntityId(item.getEntityId());
                    newTranslation.setFieldName(item.getFieldName());
                    newTranslation.setLocale(item.getLocale());
                    newTranslation.setContent(item.getContent());
                    newTranslation.setCreatedBy(actorId);
                    newTranslation.setUpdatedBy(actorId);
                    toCreate.add(newTranslation);
                    created++;
                }
            }
        }

        for (Translation translation : toCreate) {
            translationMapper.insert(translation);
        }

        for (Translation translation : toUpdate) {
            translationMapper.updateById(translation);
        }

        result.setCreated(created);
        result.setUpdated(updated);
        result.setSkipped(skipped);

        log.info("Bulk upsert completed: {} created, {} updated, {} skipped", created, updated, skipped);

        return result;
    }

    private static I18nConstants.TranslatableEntity requireEntityType(String entityType) {
        if (entityType == null) {
            throw new BusinessException(BaseErrorCode.VALIDATION_FAILED,
                    "Invalid entity type: null");
        }
        try {
            return I18nConstants.TranslatableEntity.valueOf(entityType);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(BaseErrorCode.VALIDATION_FAILED,
                    "Invalid entity type: " + entityType);
        }
    }

    private static void requireLocale(String locale) {
        if (!I18nConstants.isSupportedLocale(locale)) {
            throw new BusinessException(BaseErrorCode.VALIDATION_FAILED,
                    "Invalid locale: " + locale);
        }
    }
}
