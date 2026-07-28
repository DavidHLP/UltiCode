package com.ulticode.modules.problem.port;

import java.time.LocalDateTime;

/**
 * P3-BURNDOWN-001: owner-only write surface for the {@code test_cases}
 * rows that live in the problem module.
 *
 * <p>Before this port, {@code AdminTestCaseService} reached directly into
 * {@link com.ulticode.modules.problem.mapper.TestCaseMapper} for insert /
 * updateById / deleteById / delete-by-problem / reorder writes (8 frozen
 * ArchUnit baseline entries under rule P3-OWNER-001-F). Every admin caller
 * of these writes now goes through this port; the implementation lives in
 * the problem module and is the only admin-facing class allowed to touch
 * {@code TestCaseMapper} write methods.
 *
 * <p>Reads (list / get / export) stay in the admin read seam per the
 * sanctioned Phase 3 read-adapter pattern; this port is write-only.
 *
 * <p>Commands are primitive shapes ({@link TestCaseWrite}) so the port is
 * RPC-friendly: a future Dubbo provider (P4-RPC-001) replaces the default
 * adapter and the wire shape is unchanged.
 */
public interface TestCaseOwnerPort {

    /**
     * Insert one new test case row. The command carries the full row state
     * (the admin caller owns validation and scope-XOR invariants).
     */
    void insertTestCase(TestCaseWrite command);

    /**
     * Persist a full-row update for an existing test case (the admin caller
     * has already merged the partial DTO onto the loaded row).
     */
    void updateTestCase(TestCaseWrite command);

    /**
     * Delete one test case by primary key.
     */
    void deleteTestCase(String id);

    /**
     * Delete every test case of a problem (bulk-import replace flow).
     * Returns the number of rows deleted.
     */
    int deleteAllForProblem(Long problemId);

    /**
     * Reorder one test case: set {@code test_order} and bump
     * {@code updated_at}. Columns outside the reorder stay untouched.
     */
    void updateTestOrder(String id, int testOrder, LocalDateTime updatedAt);

    /**
     * Full-row write command for test cases. Mirrors the persisted columns
     * one-to-one so a future RPC wire shape is unchanged.
     */
    record TestCaseWrite(String id, Long problemId, boolean isSample, boolean isHidden,
                         int testOrder, String inputText, String outputText,
                         String explanation, String constraints, String inputs,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
