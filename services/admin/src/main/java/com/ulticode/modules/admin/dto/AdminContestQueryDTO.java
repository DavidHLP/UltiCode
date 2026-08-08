package com.ulticode.modules.admin.dto;

import lombok.Data;

/**
 * Query parameters for admin contest list.
 *
 * <p>Stub kept in legacy so that {@code AdminContestProjection} (and any other
 * legacy stub that references this type) can compile after the real
 * implementation was relocated to {@code backend-admin}
 * (P7-RELOCATE-ADMIN-001).
 */
@Data
public class AdminContestQueryDTO {

    /** Search by title or slug */
    private String search;

    /** Filter by contest type: PUBLIC, PRIVATE, VIRTUAL */
    private String type;

    /** Filter by status: UPCOMING, RUNNING, FINISHED */
    private String status;

    /** Page number (1-based) */
    private Integer page = 1;

    /** Number of items per page */
    private Integer limit = 10;

    /** Sort field */
    private String sortBy;

    /** Sort direction: asc or desc */
    private String sortOrder;
}
