package com.ulticode.modules.problem.port;

import com.ulticode.modules.problem.entity.Problem;

public interface ProblemWritePort {
    void insert(Problem problem);

    /**
     * Conditional owner-side update. Implementations must update the row only
     * when its current version equals {@code expectedVersion}, increment the
     * persisted version, and return the affected-row count.
     */
    int updateById(Problem problem, Long expectedVersion);

    /**
     * Conditional owner-side soft-delete. Implementations must update the row
     * only when its current version equals {@code expectedVersion}, increment
     * the persisted version, and return the affected-row count.
     */
    int deleteById(Long id, Long expectedVersion);

    Problem selectById(Long id);
    Problem selectBySlug(String slug);
}
