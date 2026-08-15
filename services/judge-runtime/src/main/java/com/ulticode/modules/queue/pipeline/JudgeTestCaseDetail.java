package com.ulticode.modules.queue.pipeline;

import com.ulticode.app.api.dto.RunResultDTO;
import com.ulticode.domain.submission.enums.CaseScope;

import java.util.List;

/**
 * Entity-free verdict detail produced by the judge runtime.
 *
 * <p>The Submission owner translates this wire shape into its entity after
 * the provider boundary. Keeping the runtime result free of
 * {@code Submission} prevents the sandbox worker from importing the business
 * persistence model.
 */
public record JudgeTestCaseDetail(
        String status,
        Integer time,
        Double memory,
        String detail,
        String output,
        String expectedOutput,
        List<RunResultDTO.RunCaseResult.InputParam> inputs,
        String caseId,
        CaseScope caseScope
) {
}
