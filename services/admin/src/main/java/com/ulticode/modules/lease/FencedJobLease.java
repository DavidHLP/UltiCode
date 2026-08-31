package com.ulticode.modules.lease;

import lombok.Data;

import java.time.LocalDateTime;

/** MyBatis row for the Admin-owned {@code fenced_job_leases} table. */
@Data
public class FencedJobLease {

    private String leaseName;
    private Long fenceToken;
    private String ownerToken;
    private LocalDateTime leasedUntil;
    private LocalDateTime updatedAt;
}
