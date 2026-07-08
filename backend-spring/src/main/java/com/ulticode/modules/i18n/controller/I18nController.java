package com.ulticode.modules.i18n.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.i18n.constants.I18nConstants;
import com.ulticode.modules.i18n.dto.BulkUpsertDTO;
import com.ulticode.modules.i18n.dto.ParseLocaleRequest;
import com.ulticode.modules.i18n.service.I18nService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * REST controller for internationalization operations.
 */
@Tag(name = "I18n", description = "Internationalization endpoints")
@RestController
@RequestMapping("/i18n")
@RequiredArgsConstructor
public class I18nController {

    private final I18nService i18nService;
    private final CurrentUserProvider currentUserProvider;

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

        // Validate entity type
        I18nConstants.TranslatableEntity type;
        try {
            type = I18nConstants.TranslatableEntity.valueOf(entityType);
        } catch (IllegalArgumentException e) {
            return Result.error(ErrorCode.I18N_INVALID_ENTITY_TYPE.getCode(),
                    ErrorCode.I18N_INVALID_ENTITY_TYPE.getMessage() + ": " + entityType);
        }

        // Validate locale
        if (!I18nConstants.isSupportedLocale(locale)) {
            return Result.error(ErrorCode.I18N_INVALID_LOCALE.getCode(),
                    ErrorCode.I18N_INVALID_LOCALE.getMessage() + ": " + locale);
        }

        Map<String, String> translations = i18nService.getTranslations(type, entityId, locale);
        return Result.success(translations);
    }

    /**
     * Bulk upsert translations.
     * <p>
     * Request fields: translations (list), skipDuplicates (boolean)
     * Response fields: created (int), updated (int), skipped (int)
     *
     * @param dto     the bulk upsert DTO
     * @param userId  the current user ID
     * @return the result with counts
     */
    @Operation(summary = "Bulk upsert translations", description = "Create or update multiple translations at once")
    @RateLimit(key = "i18n:bulk-upsert", limit = 30, period = 60)
    @PostMapping("/translations/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<BulkUpsertDTO> bulkUpsert(
            @Valid @RequestBody BulkUpsertDTO dto) {

        final String userId = currentUserProvider.getCurrentUserId();

        // Validate all translation items
        for (BulkUpsertDTO.TranslationItem item : dto.getTranslations()) {
            // Validate entity type
            I18nConstants.TranslatableEntity entityType;
            try {
                entityType = I18nConstants.TranslatableEntity.valueOf(item.getEntityType());
            } catch (IllegalArgumentException e) {
                return Result.error(ErrorCode.I18N_INVALID_ENTITY_TYPE.getCode(),
                        ErrorCode.I18N_INVALID_ENTITY_TYPE.getMessage() + ": " + item.getEntityType());
            }

            // Validate locale
            if (!I18nConstants.isSupportedLocale(item.getLocale())) {
                return Result.error(ErrorCode.I18N_INVALID_LOCALE.getCode(),
                        ErrorCode.I18N_INVALID_LOCALE.getMessage() + ": " + item.getLocale());
            }

            // Validate field name
            if (!I18nConstants.isTranslatableField(entityType, item.getFieldName())) {
                return Result.error(ErrorCode.I18N_INVALID_FIELD_NAME.getCode(),
                        ErrorCode.I18N_INVALID_FIELD_NAME.getMessage() + ": " + item.getFieldName());
            }

        }

        BulkUpsertDTO result = i18nService.bulkUpsertTranslations(
                dto.getTranslations(), dto.isSkipDuplicates(), userId);
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
    public Result<String> parseLocale(@Valid @RequestBody ParseLocaleRequest request) {
        String locale = i18nService.parseAcceptLanguage(request.getHeader());
        return Result.success(locale);
    }
}
