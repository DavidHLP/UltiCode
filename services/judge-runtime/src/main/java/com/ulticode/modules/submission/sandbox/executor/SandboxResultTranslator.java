package com.ulticode.modules.submission.sandbox.executor;

import com.ulticode.modules.submission.codec.SubmissionStatusCodec;
import com.ulticode.modules.submission.runtime.JudgeRunResponse;
import com.ulticode.modules.submission.runtime.JudgeRunRequest;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.sandbox.RunCaseResult;
import com.ulticode.modules.submission.sandbox.SandboxOutcomeClassifier;
import com.ulticode.modules.submission.sandbox.TestCase;
import com.ulticode.modules.submission.service.SandboxOutputFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal outcome-translation collaborator for {@link SandboxExecutorImpl}.
 *
 * <p>Concentrates the two pure DTO ↔ port-type translations that used to live
 * as private methods on the executor:
 * <ul>
 *   <li>{@link #toRunTestCase(TestCase)} — port {@code TestCase} → the
 *       runtime-private {@code JudgeRunRequest.TestCase} the harness speaks;
 *       and</li>
 *   <li>{@link #toPortResult(JudgeRunResponse.RunCaseResult, TestCase, long)}
 *       — runtime result DTO back into the port-owned
 *       {@link RunCaseResult} carrying the typed {@link SubmissionStatus}
 *       enum, including the ADR-002 §8 Layer-B memory-ceiling backstop
 *       delegated to {@link SandboxOutcomeClassifier#applyMemoryCeiling}.</li>
 * </ul>
 *
 * <p>This is an <em>internal</em> deepening of the sandbox module (architecture
 * review candidate #3) — not a new adapter seam. It is package-private,
 * constructed directly by {@code SandboxExecutorImpl} from collaborators it
 * already holds, so no wiring, DI, or external contract changes. The
 * security-sensitive surface (docker command, seccomp, fork detection, process
 * lifecycle) stays in {@code SandboxExecutorImpl} where ADR-002 keeps it
 * centrally owned.
 *
 * @author ulticode
 */
class SandboxResultTranslator {

    private final SandboxOutputFormatter outputFormatter;
    private final SandboxOutcomeClassifier outcomeClassifier;

    SandboxResultTranslator(SandboxOutputFormatter outputFormatter,
                            SandboxOutcomeClassifier outcomeClassifier) {
        this.outputFormatter = outputFormatter;
        this.outcomeClassifier = outcomeClassifier;
    }

    /**
     * envelope codec speaks. Lives here (not in the port) so the {@code sandbox}
     * stays decoupled from transport DTO packages in the public type
     * signatures; only this translator &mdash; which is the seam &mdash;
     * touches the runtime-private DTO type.
     *
     * @param tc the port test case
     * @return the DTO test case, never {@code null}
     */
    JudgeRunRequest.TestCase toRunTestCase(TestCase tc) {
        JudgeRunRequest.TestCase rtc = new JudgeRunRequest.TestCase();
        rtc.setId(tc.id());
        rtc.setLabel(tc.label());
        rtc.setOutput(tc.expectedOutput());
        List<JudgeRunRequest.Input> inputs = new ArrayList<>(tc.inputs().size());
        for (TestCase.Input in : tc.inputs()) {
            JudgeRunRequest.Input ri = new JudgeRunRequest.Input();
            ri.setId(in.id());
            ri.setLabel(in.label());
            ri.setName(in.name());
            ri.setValue(in.value());
            ri.setType(in.type());
            inputs.add(ri);
        }
        rtc.setInputs(inputs);
        return rtc;
    }

    /**
     * <p>Translate the runtime-private {@link JudgeRunResponse.RunCaseResult}
     * (which carries a wire-string status) into the port-owned
     * {@link RunCaseResult} (which carries a {@link SubmissionStatus} enum).</p>
     * <p>The helper writes the pre-formatted runtime / memory strings (e.g.
     * {@code "12ms"} / {@code "22.0MB"}) AND the numeric v2 fields (e.g.
     * {@code runtimeMs} / {@code memoryMb}). We prefer the numeric fields when
     * present and fall back to the formatted strings for legacy callers,
     * matching the pre-M2a behavior.
     *
     * @param dto              the wire-level result
     * @param originalCase     the originating test case (for output/input echo), may be null
     * @param memoryLimitBytes the per-run memory ceiling for the Layer-B backstop
     * @return the port-owned result, never {@code null}
     */
    RunCaseResult toPortResult(JudgeRunResponse.RunCaseResult dto,
                               TestCase originalCase, long memoryLimitBytes) {
        SubmissionStatus status = SubmissionStatusCodec.fromWire(dto.getStatus());
        long elapsedMs = dto.getRuntimeMs() != null
                ? dto.getRuntimeMs()
                : outputFormatter.parseRuntimeMs(dto.getRuntime());
        long memoryBytes = dto.getMemoryMb() != null
                ? (long) (dto.getMemoryMb() * 1024L * 1024L)
                : 0L;
        long elapsedUs = dto.getRuntimeUs() != null ? dto.getRuntimeUs() : 0L;
        long cpuMs = dto.getCpuMs() != null ? dto.getCpuMs() : 0L;
        // ADR-002 §8 Layer B: backend backstop MLE. If the harness reported a
        // peak over the limit but didn't self-classify (older harness, or a
        // language whose harness skipped the check), reclassify so the user
        // sees Memory Limit Exceeded instead of a misleading Accepted/WA.
        // Decision is owned by SandboxOutcomeClassifier.applyMemoryCeiling.
        status = outcomeClassifier.applyMemoryCeiling(status, memoryBytes, memoryLimitBytes);
        double score = status == SubmissionStatus.ACCEPTED ? 1.0 : 0.0;
        // M2a-round-2 fix (codex review F3): preserve the harness's
        // reported actual output, expected output, and the input
        // metadata so the facade can hand them back to
        // JudgeWorkerProcessor (which persists them on the
        // submission_cases row) and to the /run response.
        String output = dto.getOutput();
        String expectedOutput = originalCase == null
                ? null
                : originalCase.expectedOutput();
        List<TestCase.Input> inputs = originalCase == null
                ? null
                : originalCase.inputs();
        return new RunCaseResult(status, elapsedMs, memoryBytes,
                elapsedUs, cpuMs,
                dto.getDetail(), score, output, expectedOutput, inputs);
    }
}
