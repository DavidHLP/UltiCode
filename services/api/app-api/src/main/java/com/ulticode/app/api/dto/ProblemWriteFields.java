package com.ulticode.app.api.dto;

import java.io.Serializable;
import java.util.List;

/** Complete owner write payload. Null fields on update retain existing values. */
public record ProblemWriteFields(
        String slug, String difficulty, Boolean isPremium, Boolean isPublished,
        Boolean hasSolution, String summary, String content, String examples,
        String constraintsJson, String hints, List<Language> languages, List<String> tags)
        implements Serializable {
    private static final long serialVersionUID = 1L;
    public record Language(String language, String starterCode) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
