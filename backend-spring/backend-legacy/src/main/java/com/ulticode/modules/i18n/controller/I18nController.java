package com.ulticode.modules.i18n.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.i18n.constants.I18nConstants;
import com.ulticode.modules.i18n.dto.BulkUpsertDTO;
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
 *
 * <p>Thin HTTP adapter over the deep Translation catalog: entity-type,
 * locale, and translatable-field validation lives in {@link I18nService} so
 * the catalog is self-protecting for every caller.
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
     * @param entityType the entity type (PROBLEM, PROBLEM_DETAIL, CONTEST, SOLUTION, POST)
     * @param entityId   the entity ID
     * @param locale     the locale code (e.g., en-US, zh-CN)
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
        Map<String, String> translations = i18nService.getTranslations(entityType, entityId, locale);
        return Result.success(translations);
    }

    /**
     * Bulk upsert translations.
     * <p>
     * Request fields: translations (list), skipDuplicates (boolean)
     * Response fields: created (int), updated (int), skipped (int)
     *
     * @param dto    the bulk upsert DTO
     * @param userId the current user ID
     * @return the result with counts
     */
    @Operation(summary = "Bulk upsert translations", description = "Create or update multiple translations at once")
    @RateLimit(key = "i18n:bulk-upsert", limit = 30, period = 60)
    @PostMapping("/translations/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<BulkUpsertDTO> bulkUpsert(@Valid @RequestBody BulkUpsertDTO dto) {
        final String userId = currentUserProvider.getCurrentUserId();
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
}
