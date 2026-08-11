package com.ulticode.app.api.service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

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
     * Atomically replace every test case belonging to a problem.
     *
     * <p>The delete and inserts execute in the App owner's transaction, so a
     * failed replacement cannot leave a partially-written batch.
     */
    void replaceAllForProblem(Long problemId, List<TestCaseWrite> commands);

    /**
     * Update only a test case's order and timestamp.
     */
    void updateTestOrder(String id, int testOrder, LocalDateTime updatedAt);

    /**
     * Atomically update the order and timestamp of an ordered set of test cases.
     */
    void updateTestOrders(List<TestCaseOrder> commands);

    /**
     * Complete test-case order command. Field order is part of the contract.
     */
    record TestCaseOrder(String id, int testOrder, LocalDateTime updatedAt) implements Serializable {}

    /**
     * Complete test-case row command. Field order is part of the contract.
     */
    record TestCaseWrite(String id, Long problemId, boolean isSample, boolean isHidden,
                         int testOrder, String inputText, String outputText,
                         String explanation, String constraints, String inputs,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
