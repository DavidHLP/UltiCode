package com.ulticode.app.i18n.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO for translation data transfer.
 */
@Data
public class TranslationDTO {

    /**
     * Type of the entity being translated.
     * Values: PROBLEM, PROBLEM_DETAIL, CONTEST, SOLUTION, POST
     */
    @NotBlank(message = "Entity type is required")
    private String entityType;

    /**
     * ID of the entity being translated.
     */
    @NotBlank(message = "Entity ID is required")
    private String entityId;

    /**
     * Name of the field being translated.
     */
    @NotBlank(message = "Field name is required")
    private String fieldName;

    /**
     * Locale code for the translation.
     */
    @NotBlank(message = "Locale is required")
    private String locale;

    /**
     * The translated content.
     */
    @NotBlank(message = "Content is required")
    private String content;
}
