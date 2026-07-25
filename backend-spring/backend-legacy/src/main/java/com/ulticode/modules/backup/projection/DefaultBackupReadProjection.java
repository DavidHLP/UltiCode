package com.ulticode.modules.backup.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.backup.dto.BackupQueryDTO;
import com.ulticode.modules.backup.dto.BackupVO;
import com.ulticode.modules.backup.entity.Backup;
import com.ulticode.modules.backup.mapper.BackupMapper;
import com.ulticode.modules.backup.port.UserLookupPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link BackupReadProjection}. Owns every
 * entity-to-VO projection rule and read-side query builder for the backup
 * admin surface &mdash; see the interface javadoc for why this is a deep
 * module.
 *
 * <p>All methods are pure reads; none mutate backup state. The single-item
 * read throws {@link ErrorCode#NOT_FOUND} to preserve the access contract
 * the controller observed when it called
 * {@code BackupServiceImpl#getBackupById}.
 *
 * <p>Cross-module enrichment (the {@code createdByName} batch lookup) flows
 * through {@link UserLookupPort} so this class never imports
 * {@code com.ulticode.modules.user.mapper.UserMapper}. The user module owns
 * the read; this projection only consumes the port.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultBackupReadProjection implements BackupReadProjection {

    private final BackupMapper backupMapper;
    private final UserLookupPort userLookupPort;

    @Override
    public PageResult<BackupVO> listBackups(BackupQueryDTO query) {
        LambdaQueryWrapper<Backup> wrapper = new LambdaQueryWrapper<>();

        if (query.getType() != null) {
            wrapper.eq(Backup::getType, query.getType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Backup::getStatus, query.getStatus());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(Backup::getCreatedAt, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(Backup::getCreatedAt, query.getEndDate());
        }

        wrapper.orderByDesc(Backup::getCreatedAt);

        Page<Backup> page = new Page<>(query.getPage(), query.getLimit());
        Page<Backup> result = backupMapper.selectPage(page, wrapper);

        List<Backup> records = result.getRecords();
        Map<String, String> usernames = userLookupPort.findUsernamesByIds(
                records.stream().map(Backup::getCreatedBy).distinct().toList());

        List<BackupVO> voList = records.stream()
                .map(backup -> toVOWithUsername(backup, usernames))
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), query.getPage(), query.getLimit());
    }

    @Override
    public BackupVO getById(String id) {
        Backup backup = backupMapper.selectById(id);
        if (backup == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Backup not found");
        }
        Map<String, String> usernames = userLookupPort.findUsernamesByIds(
                List.of(backup.getCreatedBy()));
        return toVOWithUsername(backup, usernames);
    }

    @Override
    public BackupVO toVO(Backup backup) {
        if (backup == null) {
            return null;
        }
        BackupVO vo = new BackupVO();
        vo.setId(backup.getId());
        vo.setFilename(backup.getFilename());
        vo.setSize(backup.getSize());
        vo.setType(backup.getType());
        vo.setStatus(backup.getStatus());
        vo.setCreatedBy(backup.getCreatedBy());
        vo.setCreatedAt(backup.getCreatedAt());
        vo.setCompletedAt(backup.getCompletedAt());
        vo.setError(backup.getError());
        vo.setMetadata(backup.getMetadata());
        return vo;
    }

    @Override
    public List<BackupVO> toVOList(List<Backup> backups) {
        if (backups == null || backups.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, String> usernames = userLookupPort.findUsernamesByIds(
                backups.stream().map(Backup::getCreatedBy).distinct().toList());
        return backups.stream()
                .map(backup -> toVOWithUsername(backup, usernames))
                .collect(Collectors.toList());
    }

    /**
     * Apply the batched username map to the entity's {@code createdBy}.
     * Null-safe on both the map and the entity's id; missing users
     * silently leave {@code createdByName} unset (matches the previous
     * inline behavior in {@code BackupServiceImpl.toVO(Backup, Map)}).
     */
    private BackupVO toVOWithUsername(Backup backup, Map<String, String> usernames) {
        BackupVO vo = toVO(backup);
        if (vo == null || usernames == null || usernames.isEmpty()) {
            return vo;
        }
        String username = usernames.get(backup.getCreatedBy());
        if (username != null) {
            vo.setCreatedByName(username);
        }
        return vo;
    }
}
