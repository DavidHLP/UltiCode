package com.ulticode.app.i18n.controller;

import com.ulticode.app.i18n.constants.I18nConstants;
import com.ulticode.app.i18n.dto.BulkUpsertDTO;
import com.ulticode.app.i18n.service.I18nService;
import com.ulticode.common.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
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
 *
 * <p>P7-RELOCATE-I18N-001: relocated from backend-legacy to backend-app
 * alongside the service/entity/mapper/dto/constants.
 *
 * <p>Security note: @Profile("!prod") gates this endpoint until the
 * RateLimit AOP infrastructure is migrated (P7-RELOCATE-AUTH-001) and
 * JWT resource-server enforcement is wired. In prod, i18n writes remain
 * served by the legacy controller in backend-legacy (which has @RateLimit
 * and full security config) until P7-LEGACY-REMOVAL completes the cutover.
 */
@Profile("!prod")
@RestController
@RequestMapping("/i18n")
@RequiredArgsConstructor
public class I18nController {

    private final I18nService i18nService;

    /**
     * Get translations for a specific entity.
     *
     * @param entityType the entity type (PROBLEM, PROBLEM_DETAIL, CONTEST, SOLUTION, POST)
     * @param entityId   the entity ID
     * @param locale     the locale code (e.g., en-US, zh-CN)
     * @return map of field name to translated content
     */
    @GetMapping("/translations")
    public Result<Map<String, String>> getTranslations(
            @RequestParam String entityType,
            @RequestParam String entityId,
            @RequestParam String locale) {
        Map<String, String> translations = i18nService.getTranslations(entityType, entityId, locale);
        return Result.success(translations);
    }

    /**
     * Bulk upsert translations.
     *
     * <p>Note: @RateLimit(key = "i18n:bulk-upsert", limit = 30, period = 60)
     * deferred — backend-app does not yet have the RateLimit AOP infrastructure.
     * The endpoint is gated by @Profile("!prod") and @PreAuthorize as
     * compensating controls until P7-RELOCATE-AUTH-001.
     *
     * @param dto the bulk upsert DTO
     * @return the result with counts
     */
    @PostMapping("/translations/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<BulkUpsertDTO> bulkUpsert(@Valid @RequestBody BulkUpsertDTO dto) {
        BulkUpsertDTO result = i18nService.bulkUpsertTranslations(
                dto.getTranslations(), dto.isSkipDuplicates(), "system");
        return Result.success(result);
    }

    /**
     * Get supported locales.
     *
     * @return set of supported locale codes
     */
    @GetMapping("/locales")
    public Result<Set<String>> getSupportedLocales() {
        return Result.success(I18nConstants.SUPPORTED_LOCALES);
    }
}
