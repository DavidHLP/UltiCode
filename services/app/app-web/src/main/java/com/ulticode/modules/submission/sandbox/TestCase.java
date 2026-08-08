package com.ulticode.modules.submission.sandbox;

import java.util.List;

/**
 * Port-level test-case descriptor.
 *
 * <p>Decoupled from the wire DTO {@code RunSubmissionDTO.RunTestCase}
 * so the sandbox package does not depend on the controller layer and
 * so the executor can be unit-tested without the DTO class on the
 * classpath. The code-execution facade maps {@code RunTestCase →
 * TestCase} at the boundary.
 *
 * <h2>Field contract</h2>
 * <ul>
 *   <li>{@code id} — opaque per-case UUID, used as a correlation key
 *       in {@link RunCaseResult} when the caller wants to map back to
 *       the wire DTO. Required.</li>
 *   <li>{@code label} — human-readable label (e.g. {@code "Example 1"}).
 *       Optional; may be {@code null} or empty.</li>
 *   <li>{@code inputs} — ordered list of named input parameters. Order
 *       is significant: the harness binds them positionally to the
 *       solution function's argument list. May be empty for
 *       parameterless problems.</li>
 *   <li>{@code expectedOutput} — expected stdout (or whatever the
 *       harness writes to {@code /job/expected.json}). May be
 *       {@code null} for problems without an expected value
 *       (e.g. interactive / multi-shot problems); the harness then
 *       treats absence of {@code expected} as "no comparison".</li>
 * </ul>
 *
 * @see SandboxExecutor#run(SandboxJob, TestCase)
 * @see SandboxExecutor#runBatch(SandboxJob, List)
 */
public record TestCase(
        String id,
        String label,
        List<Input> inputs,
        String expectedOutput
) {
    /**
     * One named positional input parameter.
     *
     * <p>Field contract mirrors {@code RunSubmissionDTO.RunInput} but
     * kept as a port-owned type to avoid coupling the sandbox to the
     * wire DTO.
     *
     * @param id    opaque per-input UUID
     * @param label short display label
     * @param name  argument name from the problem statement
     *              (informational; the harness uses positional binding)
     * @param value stringified JSON value (number / string / list /
     *              object). The harness re-parses it.
     * @param type  optional OJ data-type hint forwarded to the
     *              D-form harness. Mirrors
     *              {@code @ulticode/sandbox-types#OJDataType}.
     *              May be {@code null} or unknown to the harness.
     */
    public record Input(
            String id,
            String label,
            String name,
            String value,
            String type
    ) {
    }
}
