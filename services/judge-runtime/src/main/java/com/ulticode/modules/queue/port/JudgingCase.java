package com.ulticode.modules.queue.port;

import com.ulticode.app.api.dto.RunSubmissionDTO;

import java.util.List;

/**
 * Queue-owned, judge-ready view of a single test case.
 *
 * <p>The judge pipeline consumes this shape alone; it never sees the
 * canonical {@code TestCase} or legacy {@code ProblemExample} entities, so
 * the Problem module's storage layout (two tables, source-selection) stays
 * behind the {@link JudgingCaseSource} seam. Inputs are pre-parsed into the
 * sandbox wire format so parsing policy concentrates in the adapters.
 */
public final class JudgingCase {

    private final String id;
    private final String label;
    private final String outputText;
    private final List<RunSubmissionDTO.RunInput> inputs;
    private final Boolean hidden;
    private final Boolean sample;

    public JudgingCase(String id, String label, String outputText,
                       List<RunSubmissionDTO.RunInput> inputs,
                       Boolean hidden, Boolean sample) {
        this.id = id;
        this.label = label;
        this.outputText = outputText;
        this.inputs = inputs;
        this.hidden = hidden;
        this.sample = sample;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getOutputText() {
        return outputText;
    }

    public List<RunSubmissionDTO.RunInput> getInputs() {
        return inputs;
    }

    public Boolean getHidden() {
        return hidden;
    }

    public Boolean getSample() {
        return sample;
    }
}
