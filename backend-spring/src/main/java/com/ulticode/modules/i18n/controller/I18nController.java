package com.ulticode.modules.i18n.controller;

import com.ulticode.common.annotation.CurrentUser;
import com.ulticode.common.response.Result;
import com.ulticode.modules.i18n.constants.I18nConstants;
import com.ulticode.modules.i18n.dto.BulkUpsertDTO;
import com.ulticode.modules.i18n.service.I18nService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * REST controller for internationalization operations.
 */
@Tag(name = "I18n", description = "Internationalization endpoints")
@RestController
@RequestMapping("/api/i18n")
@RequiredArgsConstructor
public class I18nController {

    private final I18nService i18nService;

    /**
     * Get translations for a specific entity.
     *
     * @param entityType the type of entity
     * @param entityId   the entity ID
     * @param locale     the locale code
     * @return map of field name to translated content
     */
    @Operation(summary = "Get translations", description = "Get all translations for a specific entity in a given locale")
    @GetMapping("/translations")
    public Result<Map<String, String>> getTranslations(
            @Parameter(description = "Entity type (PROBLEM, PROBLEM_DETAIL, CONTEST, SOLUTION, POST)")
            @RequestParam String entityType,
            @Parameter(description = "Entity ID")
            @RequestParam String entityId,
            @Parameter(description = "Locale code (e.g., en-US, zh-CN)")
            @RequestParam String locale) {

        try {
            I18nConstants.TranslatableEntity type = I18nConstants.TranslatableEntity.valueOf(entityType);
            Map<String, String> translations = i18nService.getTranslations(type, entityId, locale);
            return Result.success(translations);
        } catch (IllegalArgumentException e) {
            return Result.error(40000, "Invalid entity type: " + entityType);
        }
    }

    /**
     * Bulk upsert translations.
     *
     * @param dto     the bulk upsert DTO
     * @param userId  the current user ID
     * @return the result with counts
     */
    @Operation(summary = "Bulk upsert translations", description = "Create or update multiple translations at once")
    @PostMapping("/translations/bulk")
    public Result<BulkUpsertDTO> bulkUpsert(
            @Valid @RequestBody BulkUpsertDTO dto,
            @CurrentUser String userId) {

        // Set createdBy for all translations if not provided
        for (BulkUpsertDTO.TranslationItem item : dto.getTranslations()) {
            if (item.getCreatedBy() == null || item.getCreatedBy().isBlank()) {
                item.setCreatedBy(userId);
            }
        }

        BulkUpsertDTO result = i18nService.bulkUpsertTranslations(dto.getTranslations(), dto.isSkipDuplicates());
        return Result.success(result);
    }

    /**
     * Get supported locales.
     *
     * @return set of supported locale codes
     */
    @Operation(summary = "Get supported locales", description = "Get the list of supported locale codes")
    @GetMapping("/locales")
    public Result<Set<String>> getSupportedLocales() {
        return Result.success(I18nConstants.SUPPORTED_LOCALES);
    }

    /**
     * Parse locale from Accept-Language header.
     *
     * @param body the request body containing the header
     * @return the parsed locale
     */
    @Operation(summary = "Parse locale", description = "Parse Accept-Language header and return the best matching locale")
    @PostMapping("/parse-locale")
    public Result<String> parseLocale(@RequestBody Map<String, String> body) {
        String header = body.get("header");
        String locale = i18nService.parseAcceptLanguage(header);
        return Result.success(locale);
    }
}
