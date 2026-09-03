package com.ulticode.modules.problem.port;

import com.ulticode.common.dto.DifficultyCountDTO;

import java.util.List;

/**
 * Read-side port for published Problem counts grouped by difficulty.
 *
 * <p>The existing {@link DifficultyCountDTO} is reused. Providers return an
 * empty list when no published rows exist and never return {@code null}.
 */
public interface ProblemDifficultyReadPort {

    /**
     * Count published, non-deleted Problems by difficulty.
     *
     * @return difficulty/count rows, never null
     */
    List<DifficultyCountDTO> countByDifficulty();
}
