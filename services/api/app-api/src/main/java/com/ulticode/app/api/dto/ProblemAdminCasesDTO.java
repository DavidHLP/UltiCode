package com.ulticode.app.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Entity-free cases-tab projection: problem row plus ordered examples,
 * detail constraints/hints and full tag list. One bounded RPC for the Admin
 * cases tab.
 */
public record ProblemAdminCasesDTO(
        ProblemAdminRowDTO problem,
        List<ProblemAdminExampleDTO> examples,
        List<String> constraintsJson,
        List<String> hints,
        List<ProblemAdminTagDTO> tags) implements Serializable {
    private static final long serialVersionUID = 1L;


    public ProblemAdminCasesDTO {
        // examples/tags default to empty (legacy empty-list behaviour);
        // constraintsJson/hints stay nullable so the Admin edge preserves the
        // exact legacy JSON (absent vs []).
        examples = examples == null ? List.of() : List.copyOf(examples);
        hints = hints == null ? List.of() : List.copyOf(hints);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
