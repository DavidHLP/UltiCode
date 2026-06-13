package com.ulticode.modules.submission.dto;

/**
 * One positional argument in a D-form {@code input.json} case.
 *
 * <p>Matches the backend's contract for the harness
 * ({@code docker/sandbox/harness/{java,python}/}):
 * <pre>{@code
 * {
 *   "name": "head",         // logical parameter name
 *   "value": "[1,2,3]",     // JSON-encoded literal
 *   "type": "ListNode"      // optional OJ data-type hint
 * }
 * }</pre>
 *
 * <p>The harness's {@code adapt_arg} prefers {@link #type()} over a
 * Java annotation, so unannotated user code still receives a real
 * {@code ListNode} / {@code TreeNode} when the problem signature
 * demands it.
 */
public record InputSpecDTO(String name, String value, String type) {
    public InputSpecDTO {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("InputSpec.name must be non-blank");
        }
        if (value == null) {
            throw new IllegalArgumentException("InputSpec.value must be non-null (use \"null\" for JSON null)");
        }
    }
}
