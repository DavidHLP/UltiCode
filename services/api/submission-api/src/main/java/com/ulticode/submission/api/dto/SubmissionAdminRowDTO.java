package com.ulticode.submission.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity-free projection of a {@code submissions} row for the Admin
 * submission read seam.
 *
 * <p>Field set mirrors the App submission module's {@code Submission}
 * entity so the Admin edge can rebuild its wire VO (list and detail
 * shapes) without importing the entity or the module mapper. The
 * provider computes {@code codeLength} so full source code is not
 * shipped over the RPC for paginated list rows; the detail read
 * populates {@code code} / {@code notes} / percentiles / bins /
 * {@code testDetails}.
 *
 * <p>{@code problemTitle} / {@code problemSlug} are intentionally not on
 * this record: the Admin projection enriches problem display data via the
 * existing {@code ProblemAdminReadPort} contract, so the submission seam
 * stays purely submission-owned.
 */
public record SubmissionAdminRowDTO(
        String id,
        Long problemId,
        String userId,
        String language,
        String status,
        Integer runtime,
        Double memory,
        LocalDateTime createdAt,
        Integer codeLength,
        String code,
        String notes,
        Double runtimePercentile,
        Double memoryPercentile,
        List<SubmissionTestCaseDetailDTO> testDetails,
        List<Integer> memoryDistBinsMb,
        List<Integer> runtimeDistBinsMs) implements Serializable {
    private static final long serialVersionUID = 1L;


    public SubmissionAdminRowDTO {
        testDetails = testDetails == null ? List.of() : List.copyOf(testDetails);
        memoryDistBinsMb = memoryDistBinsMb == null ? null : List.copyOf(memoryDistBinsMb);
        runtimeDistBinsMs = runtimeDistBinsMs == null ? null : List.copyOf(runtimeDistBinsMs);
    }
}
