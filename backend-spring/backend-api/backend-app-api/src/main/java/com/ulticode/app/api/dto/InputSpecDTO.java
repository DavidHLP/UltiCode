package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * One positional argument in a D-form {@code input.json} case.
 */
public record InputSpecDTO(String name, String value, String type) implements Serializable {
    public InputSpecDTO {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("InputSpec.name must be non-blank");
        }
        if (value == null) {
            throw new IllegalArgumentException("InputSpec.value must be non-null (use \"null\" for JSON null)");
        }
    }
}
