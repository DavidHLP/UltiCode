package com.ulticode.app.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Entity-free description-tab projection: problem row plus its detail
 * (statement / constraints / hints), full tag list and ordered examples.
 *
 * <p>One composed payload per problem so the Admin description tab costs a
 * single bounded RPC instead of an N+1 fan of per-entity reads. {@code tags}
 * and {@code examples} are never null (empty list when absent); detail is
 * {@code null} when the problem has no {@code problem_details} row.
 */
public record ProblemAdminDescriptionDTO(
        ProblemAdminRowDTO problem,
        String summary,
        String content,
        List<String> constraintsJson,
        List<String> hints,
        List<ProblemAdminTagDTO> tags,
        List<ProblemAdminExampleDTO> examples) implements Serializable {

    public ProblemAdminDescriptionDTO {
        // tags/examples default to empty (legacy empty-list behaviour);
        // constraintsJson/hints stay nullable so the Admin edge preserves the
        // exact legacy JSON (absent vs []).
        tags = tags == null ? List.of() : List.copyOf(tags);
        examples = examples == null ? List.of() : List.copyOf(examples);
    }
}
