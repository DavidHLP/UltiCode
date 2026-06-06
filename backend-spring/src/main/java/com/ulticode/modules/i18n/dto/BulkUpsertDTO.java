package com.ulticode.modules.i18n.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * DTO for bulk upsert operations on translations.
 */
@Data
public class BulkUpsertDTO {

    /**
     * List of translations to upsert.
     */
    @NotEmpty(message = "Translations list cannot be empty")
    @Valid
    private List<TranslationItem> translations;

    /**
     * Whether to skip duplicate entries.
     * If true, duplicates will be skipped instead of updated.
     */
    private boolean skipDuplicates = false;

    /**
     * Number of translations created.
     */
    private int created;

    /**
     * Number of translations updated.
     */
    private int updated;

    /**
     * Number of translations skipped.
     */
    private int skipped;

    /**
     * Inner class representing a single translation item.
     */
    @Data
    public static class TranslationItem {

        /**
         * Type of the entity being translated.
         */
        private String entityType;

        /**
         * ID of the entity being translated.
         */
        private String entityId;

        /**
         * Name of the field being translated.
         */
        private String fieldName;

        /**
         * Locale code for the translation.
         */
        private String locale;

        /**
         * The translated content.
         */
        private String content;

    }
}
