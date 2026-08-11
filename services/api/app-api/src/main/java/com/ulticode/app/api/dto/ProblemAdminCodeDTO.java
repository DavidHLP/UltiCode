package com.ulticode.app.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Entity-free code-data projection: problem row plus its starter-code
 * languages. One bounded RPC for the Admin code tab.
 */
public record ProblemAdminCodeDTO(
        ProblemAdminRowDTO problem,
        List<ProblemAdminLanguageDTO> languages) implements Serializable {

    public ProblemAdminCodeDTO {
        languages = languages == null ? List.of() : List.copyOf(languages);
    }
}
