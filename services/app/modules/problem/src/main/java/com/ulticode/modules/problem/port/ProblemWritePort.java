package com.ulticode.modules.problem.port;

import com.ulticode.modules.problem.entity.Problem;

public interface ProblemWritePort {
    void insert(Problem problem);

    /**
     * Legacy unconditional update retained for in-module callers that have
     * not migrated to the owner-side optimistic-lock fence.
     */
    void updateById(Problem problem);

    /**
     * Conditional owner-side update. Implementations must update the row only
     * when its current version equals {@code expectedVersion}, increment the
     * persisted version, and return the affected-row count.
     */
    int updateById(Problem problem, Long expectedVersion);

    /**
     * Legacy unconditional soft-delete retained for old callers.
     */
    void deleteById(Long id);

    /**
     * Conditional owner-side soft-delete. Implementations must update the row
     * only when its current version equals {@code expectedVersion}, increment
     * the persisted version, and return the affected-row count.
     */
    int deleteById(Long id, Long expectedVersion);

    Problem selectById(Long id);
    Problem selectBySlug(String slug);
}
