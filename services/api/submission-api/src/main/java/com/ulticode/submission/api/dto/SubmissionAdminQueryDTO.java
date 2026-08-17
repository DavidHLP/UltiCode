package com.ulticode.submission.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Entity-free Admin submission query filters.
 *
 * <p>Plain POJO (not a record) so Spring MVC can bind it directly as a
 * {@code @ModelAttribute} from query parameters, exactly like the legacy
 * admin {@code AdminSubmissionQueryDTO} it replaces. Field set, defaults
 * and sort semantics mirror that DTO so the Admin HTTP surface is
 * unchanged.
 */
@Data
public class SubmissionAdminQueryDTO implements Serializable {
    private static final long serialVersionUID = 1L;


    /** Search by submission id / language / problem title. */
    private String search;

    /** Filter by user ID. */
    private String userId;

    /** Filter by problem ID. */
    private Long problemId;

    /** Filter by status (Pending, Accepted, Wrong Answer, etc.). */
    private String status;

    /** Filter by programming language. */
    private String language;

    /** Filter by start date (submission created after). */
    private LocalDateTime startDate;

    /** Filter by end date (submission created before). */
    private LocalDateTime endDate;

    /** Page number (1-based). */
    private Integer page = 1;

    /** Items per page. */
    private Integer limit = 10;

    /** Sort by field (createdAt, runtime, memory, status). */
    private String sortBy = "createdAt";

    /** Sort order (asc, desc). */
    private String sortOrder = "desc";
}
