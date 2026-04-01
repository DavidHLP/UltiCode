package com.ulticode.modules.solution.dto;

import lombok.Data;

/**
 * Request DTO for recording a solution view.
 */
@Data
public class RecordViewRequest {

    /**
     * User ID who is viewing (optional, can be null for anonymous)
     */
    private String userId;
}