package com.ulticode.app.i18n.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.app.i18n.constants.I18nConstants;
import com.ulticode.app.i18n.dto.BulkUpsertDTO;
import com.ulticode.app.i18n.entity.Translation;
import com.ulticode.app.i18n.mapper.TranslationMapper;
import com.ulticode.app.i18n.service.impl.I18nServiceImpl;
import com.ulticode.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for I18nService (relocated from backend-legacy).
 */
@ExtendWith(MockitoExtension.class)
class I18nServiceTest {

    @Mock
    private TranslationMapper translationMapper;

    @InjectMocks
    private I18nServiceImpl i18nService;

    private List<Translation> testTranslations;

    @BeforeEach
    void setUp() {
        Translation titleTranslation = new Translation();
        titleTranslation.setId("title-trans-id");
        titleTranslation.setEntityType("PROBLEM");
        titleTranslation.setEntityId("problem-1");
        titleTranslation.setFieldName("title");
        titleTranslation.setLocale("zh-CN");
        titleTranslation.setContent("Chinese Title");

        Translation summaryTranslation = new Translation();
        summaryTranslation.setId("summary-trans-id");
        summaryTranslation.setEntityType("PROBLEM");
        summaryTranslation.setEntityId("problem-1");
        summaryTranslation.setFieldName("summary");
        summaryTranslation.setLocale("zh-CN");
        summaryTranslation.setContent("Chinese Summary");

        testTranslations = Arrays.asList(titleTranslation, summaryTranslation);
    }

    @Nested
    @DisplayName("getTranslations")
    class GetTranslationsTests {

        @Test
        @DisplayName("should return translations for valid entity")
        void shouldReturnTranslationsForValidEntity() {
            when(translationMapper.findByEntityAndLocale("PROBLEM", "problem-1", "zh-CN"))
                    .thenReturn(testTranslations);

            Map<String, String> result = i18nService.getTranslations("PROBLEM", "problem-1", "zh-CN");

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals("Chinese Title", result.get("title"));
            assertEquals("Chinese Summary", result.get("summary"));
        }

        @Test
        @DisplayName("should return empty map for non-existent entity")
        void shouldReturnEmptyMapForNonExistentEntity() {
            when(translationMapper.findByEntityAndLocale("PROBLEM", "non-existent", "zh-CN"))
                    .thenReturn(Collections.emptyList());

            Map<String, String> result = i18nService.getTranslations("PROBLEM", "non-existent", "zh-CN");

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty map for null entityId without touching the mapper")
        void shouldReturnEmptyMapForNullEntityId() {
            Map<String, String> result = i18nService.getTranslations("PROBLEM", null, "zh-CN");

            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(translationMapper, never()).findByEntityAndLocale(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should reject an invalid entity type before lookup")
        void shouldRejectInvalidEntityType() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> i18nService.getTranslations("NOT_A_TYPE", "problem-1", "zh-CN"));
            assertEquals(49999, ex.getCode());
            verify(translationMapper, never()).findByEntityAndLocale(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should reject an unsupported locale before lookup")
        void shouldRejectUnsupportedLocale() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> i18nService.getTranslations("PROBLEM", "problem-1", "fr-FR"));
            assertEquals(49999, ex.getCode());
            verify(translationMapper, never()).findByEntityAndLocale(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("bulkUpsertTranslations")
    class BulkUpsertTranslationsTests {

        @Test
        @DisplayName("should create new translations")
        void shouldCreateNewTranslations() {
            List<BulkUpsertDTO.TranslationItem> items = new ArrayList<>();

            BulkUpsertDTO.TranslationItem item = new BulkUpsertDTO.TranslationItem();
            item.setEntityType("PROBLEM");
            item.setEntityId("problem-1");
            item.setFieldName("title");
            item.setLocale("zh-CN");
            item.setContent("New Translation");
            items.add(item);

            when(translationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            when(translationMapper.insert(any(Translation.class))).thenReturn(1);

            BulkUpsertDTO result = i18nService.bulkUpsertTranslations(items, false, "user-1");

            assertNotNull(result);
            assertEquals(1, result.getCreated());
            assertEquals(0, result.getUpdated());
            assertEquals(0, result.getSkipped());
        }

        @Test
        @DisplayName("should update existing translations")
        void shouldUpdateExistingTranslations() {
            List<BulkUpsertDTO.TranslationItem> items = new ArrayList<>();

            BulkUpsertDTO.TranslationItem item = new BulkUpsertDTO.TranslationItem();
            item.setEntityType("PROBLEM");
            item.setEntityId("problem-1");
            item.setFieldName("title");
            item.setLocale("zh-CN");
            item.setContent("Updated Translation");
            items.add(item);

            Translation existing = new Translation();
            existing.setId("existing-id");
            existing.setEntityType("PROBLEM");
            existing.setEntityId("problem-1");
            existing.setFieldName("title");
            existing.setLocale("zh-CN");
            existing.setContent("Old Translation");

            when(translationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(existing));
            when(translationMapper.updateById(any(Translation.class))).thenReturn(1);

            BulkUpsertDTO result = i18nService.bulkUpsertTranslations(items, false, "user-1");

            assertNotNull(result);
            assertEquals(0, result.getCreated());
            assertEquals(1, result.getUpdated());
            assertEquals(0, result.getSkipped());
            assertEquals("user-1", existing.getUpdatedBy());
        }

        @Test
        @DisplayName("should skip duplicates when flag is set")
        void shouldSkipDuplicatesWhenFlagIsSet() {
            List<BulkUpsertDTO.TranslationItem> items = new ArrayList<>();

            BulkUpsertDTO.TranslationItem item = new BulkUpsertDTO.TranslationItem();
            item.setEntityType("PROBLEM");
            item.setEntityId("problem-1");
            item.setFieldName("title");
            item.setLocale("zh-CN");
            item.setContent("Updated Translation");
            items.add(item);

            Translation existing = new Translation();
            existing.setId("existing-id");
            existing.setEntityType("PROBLEM");
            existing.setEntityId("problem-1");
            existing.setFieldName("title");
            existing.setLocale("zh-CN");
            existing.setContent("Old Translation");

            when(translationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(existing));

            BulkUpsertDTO result = i18nService.bulkUpsertTranslations(items, true, "user-1");

            assertNotNull(result);
            assertEquals(0, result.getCreated());
            assertEquals(0, result.getUpdated());
            assertEquals(1, result.getSkipped());
        }

        @Test
        @DisplayName("should handle empty list")
        void shouldHandleEmptyList() {
            BulkUpsertDTO result =
                    i18nService.bulkUpsertTranslations(Collections.emptyList(), false, "user-1");

            assertNotNull(result);
            assertEquals(0, result.getCreated());
            assertEquals(0, result.getUpdated());
            assertEquals(0, result.getSkipped());
        }

        @Test
        @DisplayName("should handle null list")
        void shouldHandleNullList() {
            BulkUpsertDTO result = i18nService.bulkUpsertTranslations(null, false, "user-1");

            assertNotNull(result);
            assertEquals(0, result.getCreated());
            assertEquals(0, result.getUpdated());
            assertEquals(0, result.getSkipped());
        }

        @Test
        @DisplayName("should abort the batch on an invalid field name without persisting")
        void shouldAbortOnInvalidFieldName() {
            List<BulkUpsertDTO.TranslationItem> items = new ArrayList<>();
            BulkUpsertDTO.TranslationItem item = new BulkUpsertDTO.TranslationItem();
            item.setEntityType("PROBLEM");
            item.setEntityId("problem-1");
            item.setFieldName("notTranslatable");
            item.setLocale("zh-CN");
            item.setContent("X");
            items.add(item);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> i18nService.bulkUpsertTranslations(items, false, "user-1"));
            assertEquals(49999, ex.getCode());
            verify(translationMapper, never()).insert(any(Translation.class));
            verify(translationMapper, never()).updateById(any(Translation.class));
        }
    }
}
