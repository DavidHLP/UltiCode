package com.ulticode.app.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Entity-free test-case row projection for administrative consumers.
 *
 * <p>Read-side mirror of {@code TestCaseOwnerPort.TestCaseWrite}: the same
 * row shape the write contract accepts, returned by the read operations.
 * {@code isDeleted}/{@code deletedAt} are carried for wire compatibility
 * with the legacy entity serialization; MyBatis-Plus {@code @TableLogic}
 * guarantees they are null for every live row.
 */
public record ProblemAdminTestCaseDTO(
        String id,
        Long problemId,
        Boolean isSample,
        Boolean isHidden,
        Integer testOrder,
        String inputText,
        String outputText,
        String inputs,
        String explanation,
        String constraints,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Boolean isDeleted,
        LocalDateTime deletedAt) implements Serializable {
}
