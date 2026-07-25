package com.ulticode.modules.backup.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.backup.dto.BackupQueryDTO;
import com.ulticode.modules.backup.dto.BackupVO;
import com.ulticode.modules.backup.entity.Backup;

import java.util.List;

/**
 * Read-side projection for the backup module &mdash; a deep module that owns
 * every entity-to-VO projection rule and read-side query builder for the
 * backup admin surface. Mirrors the
 * {@link com.ulticode.modules.moderation.projection.ModerationProjection} /
 * {@link com.ulticode.modules.admin.projection.AdminUserProjection} shape
 * exactly.
 *
 * <p>Previously the same shallow cluster lived inline in
 * {@code BackupServiceImpl}: the paginated list query
 * ({@link #listBackups}, with the {@code type} / {@code status} /
 * {@code startDate} / {@code endDate} filter chain and the
 * {@code createdAt}-desc sort), the single-detail read
 * ({@link #getById}, with the {@code NotFound} check), and the
 * batched user-lookup for {@code createdByName} enrichment &mdash; all of
 * which sat next to the write state machine ({@code createBackup} /
 * {@code restoreBackup} / {@code deleteBackup} / {@code executeBackup}) in
 * the same 311-line file. After the deepening:
 *
 * <ul>
 *   <li>{@code BackupServiceImpl} keeps the write state machine only.
 *       Write paths ({@code createBackup}, {@code restoreBackup}) return
 *       the post-write VO by delegating to {@link #toVO(Backup)} and never
 *       call the projection's query helpers directly.</li>
 *   <li>{@code BackupController} depends on this projection directly for
 *       reads and on the service for writes &mdash; the convention set by
 *       {@code ModerationController} and {@code UserManagementController}.</li>
 *   <li>Cross-module enrichment (the {@code createdByName} batch lookup) is
 *       inverted through {@link com.ulticode.modules.backup.port.UserLookupPort}
 *       so {@code BackupServiceImpl} no longer reaches into
 *       {@code UserMapper} directly.</li>
 * </ul>
 *
 * <p>All methods are pure reads; none mutate backup state. The single-item
 * read throws {@link com.ulticode.common.exception.ErrorCode#NOT_FOUND} to
 * preserve the access contract observed by the controller and by any caller
 * that used to call {@code BackupServiceImpl#getBackupById}.
 *
 * @author ulticode
 * @see com.ulticode.modules.moderation.projection.ModerationProjection
 * @see com.ulticode.modules.admin.projection.AdminUserProjection
 */
public interface BackupReadProjection {

    /**
     * List backups with pagination, filters ({@code type} / {@code status}
     * / {@code startDate} / {@code endDate}) and {@code createdAt}-desc
     * sort &mdash; the same shape the controller used to get from
     * {@code BackupServiceImpl#getBackups}. Enriches each row with the
     * creating user's username by batch-loading the unique
     * {@code createdBy} ids via
     * {@link com.ulticode.modules.backup.port.UserLookupPort}.
     *
     * @param query the query parameters including filters and pagination
     * @return paginated result of backup VOs (with {@code createdByName}
     *         populated where known); never {@code null}
     */
    PageResult<BackupVO> listBackups(BackupQueryDTO query);

    /**
     * Get a single backup by id, projected with its creating user's
     * username. Facade for the controller's read-by-id path so it crosses
     * the seam instead of reaching back into the service.
     *
     * @param id the backup id
     * @return the backup VO with {@code createdByName}; never {@code null}
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.common.exception.ErrorCode#NOT_FOUND}
     *         ("Backup not found") when the backup does not exist
     */
    BackupVO getById(String id);

    /**
     * Project a single {@link Backup} entity to its VO with no
     * cross-module enrichment. Facade for the write paths
     * ({@code createBackup} / {@code restoreBackup}) so they return the
     * same view shape the read paths serve, without holding the projection
     * rules themselves.
     *
     * @param backup the backup entity; may be {@code null}
     * @return the backup VO, or {@code null} if the input is {@code null}
     */
    BackupVO toVO(Backup backup);

    /**
     * Batch projection of a pre-fetched list of entities with the same
     * user enrichment as the controller-facing list path. Useful when an
     * external caller (e.g. a future read API) already has the entities
     * in memory and only needs the {@code createdByName} enrichment.
     *
     * @param backups the backup entities; may be empty or {@code null}
     * @return the projected VOs; never {@code null}
     */
    List<BackupVO> toVOList(List<Backup> backups);
}
