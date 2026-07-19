package com.ulticode.modules.i18n.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.i18n.constants.I18nConstants;
import com.ulticode.modules.i18n.dto.BulkUpsertDTO;
import com.ulticode.modules.i18n.entity.Translation;
import com.ulticode.modules.i18n.mapper.TranslationMapper;
import com.ulticode.modules.i18n.service.impl.I18nServiceImpl;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for I18nService.
 */
@ExtendWith(MockitoExtension.class)
class I18nServiceTest {

    @Mock
    private TranslationMapper translationMapper;

    @InjectMocks
    private I18nServiceImpl i18nService;

    private Translation testTranslation;
    private List<Translation> testTranslations;

    @BeforeEach
    void setUp() {
        testTranslation = new Translation();
        testTranslation.setId("test-translation-id");
        testTranslation.setEntityType("PROBLEM");
        testTranslation.setEntityId("problem-1");
        testTranslation.setFieldName("title");
        testTranslation.setLocale("zh-CN");
        testTranslation.setContent("Test Title Chinese");
        testTranslation.setCreatedBy("user-1");

        testTranslations = new ArrayList<>();
        testTranslations.add(testTranslation);

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
            // Arrange
            when(translationMapper.findByEntityAndLocale("PROBLEM", "problem-1", "zh-CN"))
                    .thenReturn(testTranslations);

            // Act
            Map<String, String> result = i18nService.getTranslations(
                    I18nConstants.TranslatableEntity.PROBLEM,
                    "problem-1",
                    "zh-CN"
            );

            // Assert
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals("Chinese Title", result.get("title"));
            assertEquals("Chinese Summary", result.get("summary"));
        }

        @Test
        @DisplayName("should return empty map for non-existent entity")
        void shouldReturnEmptyMapForNonExistentEntity() {
            // Arrange
            when(translationMapper.findByEntityAndLocale("PROBLEM", "non-existent", "zh-CN"))
                    .thenReturn(Collections.emptyList());

            // Act
            Map<String, String> result = i18nService.getTranslations(
                    I18nConstants.TranslatableEntity.PROBLEM,
                    "non-existent",
                    "zh-CN"
            );

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty map for null entityId")
        void shouldReturnEmptyMapForNullEntityId() {
            // Act
            Map<String, String> result = i18nService.getTranslations(
                    I18nConstants.TranslatableEntity.PROBLEM,
                    null,
                    "zh-CN"
            );

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(translationMapper, never()).findByEntityAndLocale(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should return empty map for blank locale")
        void shouldReturnEmptyMapForBlankLocale() {
            // Act
            Map<String, String> result = i18nService.getTranslations(
                    I18nConstants.TranslatableEntity.PROBLEM,
                    "problem-1",
                    ""
            );

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("bulkUpsertTranslations")
    class BulkUpsertTranslationsTests {

        @Test
        @DisplayName("should create new translations")
        void shouldCreateNewTranslations() {
            // Arrange
            List<BulkUpsertDTO.TranslationItem> items = new ArrayList<>();

            BulkUpsertDTO.TranslationItem item = new BulkUpsertDTO.TranslationItem();
            item.setEntityType("PROBLEM");
            item.setEntityId("problem-1");
            item.setFieldName("title");
            item.setLocale("zh-CN");
            item.setContent("New Translation");
            items.add(item);

            // Return empty list to indicate no existing translations (batch query)
            when(translationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            when(translationMapper.insert(any(Translation.class))).thenReturn(1);

            // Act
            BulkUpsertDTO result = i18nService.bulkUpsertTranslations(items, false, "user-1");

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getCreated());
            assertEquals(0, result.getUpdated());
            assertEquals(0, result.getSkipped());
        }

        @Test
        @DisplayName("should update existing translations")
        void shouldUpdateExistingTranslations() {
            // Arrange
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

            // Return existing translation in batch query
            when(translationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(existing));
            when(translationMapper.updateById(any(Translation.class))).thenReturn(1);

            // Act
            BulkUpsertDTO result = i18nService.bulkUpsertTranslations(items, false, "user-1");

            // Assert
            assertNotNull(result);
            assertEquals(0, result.getCreated());
            assertEquals(1, result.getUpdated());
            assertEquals(0, result.getSkipped());
            assertEquals("user-1", existing.getUpdatedBy());
        }

        @Test
        @DisplayName("should skip duplicates when flag is set")
        void shouldSkipDuplicatesWhenFlagIsSet() {
            // Arrange
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

            // Return existing translation in batch query
            when(translationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(existing));

            // Act
            BulkUpsertDTO result = i18nService.bulkUpsertTranslations(items, true, "user-1");

            // Assert
            assertNotNull(result);
            assertEquals(0, result.getCreated());
            assertEquals(0, result.getUpdated());
            assertEquals(1, result.getSkipped());
        }

        @Test
        @DisplayName("should handle empty list")
        void shouldHandleEmptyList() {
            // Act
            BulkUpsertDTO result =
                    i18nService.bulkUpsertTranslations(Collections.emptyList(), false, "user-1");

            // Assert
            assertNotNull(result);
            assertEquals(0, result.getCreated());
            assertEquals(0, result.getUpdated());
            assertEquals(0, result.getSkipped());
        }

        @Test
        @DisplayName("should handle null list")
        void shouldHandleNullList() {
            // Act
            BulkUpsertDTO result = i18nService.bulkUpsertTranslations(null, false, "user-1");

            // Assert
            assertNotNull(result);
            assertEquals(0, result.getCreated());
            assertEquals(0, result.getUpdated());
            assertEquals(0, result.getSkipped());
        }
    }

    @Nested
    @DisplayName("parseAcceptLanguage")
    class ParseAcceptLanguageTests {

        @Test
        @DisplayName("should parse valid header")
        void shouldParseValidHeader() {
            // Act
            String result = i18nService.parseAcceptLanguage("zh-CN,en-US;q=0.9");

            // Assert
            assertEquals("zh-CN", result);
        }

        @Test
        @DisplayName("should return fallback for invalid header")
        void shouldReturnFallbackForInvalidHeader() {
            // Act
            String result = i18nService.parseAcceptLanguage("invalid-locale");

            // Assert
            assertEquals("en-US", result);
        }

        @Test
        @DisplayName("should handle null header")
        void shouldHandleNullHeader() {
            // Act
            String result = i18nService.parseAcceptLanguage(null);

            // Assert
            assertEquals("en-US", result);
        }

        @Test
        @DisplayName("should extract first matching locale")
        void shouldExtractFirstMatchingLocale() {
            // Act
            String result = i18nService.parseAcceptLanguage("ja-JP,zh-CN;q=0.8,en-US;q=0.7");

            // Assert
            assertEquals("ja-JP", result);
        }

        @Test
        @DisplayName("should handle empty header")
        void shouldHandleEmptyHeader() {
            // Act
            String result = i18nService.parseAcceptLanguage("");

            // Assert
            assertEquals("en-US", result);
        }
    }
}
