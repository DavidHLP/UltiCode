package com.ulticode.modules.problem.service;

import com.ulticode.modules.problem.vo.ProblemVersionDetailVO;
import com.ulticode.modules.problem.vo.ProblemVersionVO;
import com.ulticode.modules.problem.vo.VersionWithDiffVO;
import com.ulticode.modules.problem.vo.VersionsResponseVO;

/**
 * Service interface for problem version history operations.
 * Provides CRUD, diff comparison, and rollback capabilities for problem snapshots.
 */
public interface ProblemVersionService {

    /**
     * List version history for a problem with pagination.
     *
     * @param problemId the problem ID
     * @param page      the page number (1-based)
     * @param limit     the page size
     * @return paginated version list response
     */
    VersionsResponseVO listVersions(Long problemId, Integer page, Integer limit);

    /**
     * Get full detail of a specific version including deserialized snapshot data.
     *
     * @param problemId the problem ID
     * @param versionId the version record ID
     * @return version detail with full snapshot data
     */
    ProblemVersionDetailVO getVersionDetail(Long problemId, String versionId);

    /**
     * Compare two versions and return their differences.
     *
     * @param problemId     the problem ID
     * @param fromVersionId the source version record ID
     * @param toVersionId   the target version record ID
     * @return comparison result with both versions and their differences
     */
    VersionWithDiffVO compareVersions(Long problemId, String fromVersionId, String toVersionId);

    /**
     * Rollback a problem to a specific version and create a new ROLLBACK version record.
     *
     * @param problemId  the problem ID
     * @param versionId  the target version record ID to roll back to
     * @param reason     the rollback reason
     * @param operatorId the ID of the operator performing the rollback
     * @return the newly created rollback version
     */
    ProblemVersionVO rollbackToVersion(Long problemId, String versionId, String reason, String operatorId);

    /**
     * Create the initial version (versionNumber = 1, changeType = CREATE) for a problem.
     *
     * @param problemId  the problem ID
     * @param operatorId the ID of the operator creating the version
     * @return the created initial version
     */
    ProblemVersionVO createInitialVersion(Long problemId, String operatorId);

    /**
     * Create a new version snapshot for a problem.
     *
     * @param problemId      the problem ID
     * @param changeType     the type of change (e.g., UPDATE, ROLLBACK)
     * @param changeSummary  the summary of changes
     * @param operatorId     the ID of the operator creating the version
     * @return the created version
     */
    ProblemVersionVO createVersion(Long problemId, String changeType, String changeSummary, String operatorId);
}
