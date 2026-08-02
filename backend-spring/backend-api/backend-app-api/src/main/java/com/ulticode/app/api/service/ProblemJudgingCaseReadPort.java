package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.ProblemJudgingCaseDTO;

import java.util.List;

/**
 * Read-side port for raw Problem test cases consumed by the judge queue.
 *
 * <p>The queue remains the owner of input parsing and judge-ready case
 * construction. An absent or empty case set is represented by an empty list;
 * the judge pipeline treats that result as fail-closed system error. The
 * result is never {@code null}.
 */
public interface ProblemJudgingCaseReadPort {

    /**
     * Load Problem test cases in their persisted run order.
     *
     * @param problemId numeric Problem ID
     * @return raw cases in run order, never null
     */
    List<ProblemJudgingCaseDTO> loadCases(long problemId);
}
