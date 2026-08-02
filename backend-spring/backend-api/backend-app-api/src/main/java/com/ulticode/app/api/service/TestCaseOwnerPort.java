package com.ulticode.app.api.service;

import java.time.LocalDateTime;

/**
 * Owner-only write surface for the {@code test_cases} table.
 *
 * <p>The command is a complete row shape. The Problem provider owns the
 * implementation, while administrative consumers depend only on this
 * entity-free contract.
 */
public interface TestCaseOwnerPort {

    /**
     * Insert one complete test-case row.
     */
    void insertTestCase(TestCaseWrite command);

    /**
     * Persist one complete test-case row update.
     */
    void updateTestCase(TestCaseWrite command);

    /**
     * Delete one test case by primary key.
     */
    void deleteTestCase(String id);

    /**
     * Delete every test case belonging to a problem.
     *
     * @return the number of rows deleted
     */
    int deleteAllForProblem(Long problemId);

    /**
     * Update only a test case's order and timestamp.
     */
    void updateTestOrder(String id, int testOrder, LocalDateTime updatedAt);

    /**
     * Complete test-case row command. Field order is part of the contract.
     */
    record TestCaseWrite(String id, Long problemId, boolean isSample, boolean isHidden,
                         int testOrder, String inputText, String outputText,
                         String explanation, String constraints, String inputs,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
