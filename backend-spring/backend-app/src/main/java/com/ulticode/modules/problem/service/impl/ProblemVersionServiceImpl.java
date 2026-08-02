package com.ulticode.modules.problem.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.app.error.ProblemErrorCode;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemVersion;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemVersionMapper;
import com.ulticode.modules.problem.service.ProblemSnapshotService;
import com.ulticode.modules.problem.service.ProblemVersionService;
import com.ulticode.modules.problem.vo.ProblemVersionDetailVO;
import com.ulticode.modules.problem.vo.ProblemVersionVO;
import com.ulticode.modules.problem.vo.VersionDiffVO;
import com.ulticode.modules.problem.vo.VersionWithDiffVO;
import com.ulticode.modules.problem.vo.VersionsResponseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemVersionServiceImpl implements ProblemVersionService {

    private final ProblemMapper problemMapper;
    private final ProblemVersionMapper problemVersionMapper;
    private final ProblemSnapshotService snapshotService;

    @Override
    public VersionsResponseVO listVersions(Long problemId, Integer page, Integer limit) {
        PaginationRequest pageRequest = PaginationRequest.of(page, limit);
        int currentPage = pageRequest.page();
        int currentLimit = pageRequest.pageSize();

        Page<ProblemVersion> versionPage = new Page<>(currentPage, currentLimit);
        Page<ProblemVersion> result = problemVersionMapper.selectByProblemId(problemId, versionPage);

        List<ProblemVersionVO> items = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        VersionsResponseVO.Pagination pagination = new VersionsResponseVO.Pagination();
        pagination.setTotal(result.getTotal());
        pagination.setPage(currentPage);
        pagination.setLimit(currentLimit);
        pagination.setTotalPages((int) Math.ceil((double) result.getTotal() / currentLimit));

        VersionsResponseVO response = new VersionsResponseVO();
        response.setVersions(items);
        response.setPagination(pagination);
        return response;
    }

    @Override
    public ProblemVersionDetailVO getVersionDetail(Long problemId, String versionId) {
        Long id = Long.parseLong(versionId);
        ProblemVersion version = problemVersionMapper.selectById(id);
        if (version == null || !version.getProblemId().equals(problemId)) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Version not found");
        }

        ProblemVersionDetailVO detailVO = new ProblemVersionDetailVO();
        detailVO.setId(String.valueOf(version.getId()));
        detailVO.setVersionNumber(version.getVersionNumber());
        detailVO.setChangeType(version.getChangeType());
        detailVO.setChangeSummary(version.getChangeSummary());
        detailVO.setCreatedAt(version.getCreatedAt() != null ? version.getCreatedAt().toString() : null);
        detailVO.setCreatedBy(version.getCreatedBy());

        snapshotService.populateDetail(detailVO, version.getSnapshotJson());

        return detailVO;
    }

    @Override
    public VersionWithDiffVO compareVersions(Long problemId, String fromVersionId, String toVersionId) {
        Long fromId = Long.parseLong(fromVersionId);
        Long toId = Long.parseLong(toVersionId);

        ProblemVersion fromVersion = problemVersionMapper.selectById(fromId);
        ProblemVersion toVersion = problemVersionMapper.selectById(toId);

        if (fromVersion == null || !fromVersion.getProblemId().equals(problemId)) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "From version not found");
        }
        if (toVersion == null || !toVersion.getProblemId().equals(problemId)) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "To version not found");
        }

        List<VersionDiffVO> diffs = snapshotService.diff(fromVersion.getSnapshotJson(), toVersion.getSnapshotJson());

        VersionWithDiffVO result = new VersionWithDiffVO();
        result.setFromVersion(toVO(fromVersion));
        result.setToVersion(toVO(toVersion));
        result.setDiffs(diffs);
        return result;
    }

    @Override
    @Transactional
    public ProblemVersionVO rollbackToVersion(Long problemId, String versionId, String reason, String operatorId) {
        Long id = Long.parseLong(versionId);
        ProblemVersion targetVersion = problemVersionMapper.selectById(id);
        if (targetVersion == null || !targetVersion.getProblemId().equals(problemId)) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Version not found");
        }

        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new BusinessException(ProblemErrorCode.PROBLEM_NOT_FOUND);
        }

        snapshotService.restore(problemId, targetVersion.getSnapshotJson());

        String changeSummary = reason != null ? reason : "Rollback to version " + targetVersion.getVersionNumber();
        return createVersion(problemId, "ROLLBACK", changeSummary, operatorId);
    }

    @Override
    @Transactional
    public ProblemVersionVO createInitialVersion(Long problemId, String operatorId) {
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new BusinessException(ProblemErrorCode.PROBLEM_NOT_FOUND);
        }

        // Bug #3 修复：重复调用应返回业务错误而非 500 (uk_problem_version 唯一约束)
        Integer existing = problemVersionMapper.selectLatestVersionNumber(problemId);
        if (existing != null && existing >= 1) {
            log.warn("Initial version already exists for problem {} (latest versionNumber={})",
                    problemId, existing);
            throw new BusinessException(ProblemErrorCode.PROBLEM_VERSION_ALREADY_EXISTS,
                    "Initial version already exists for problem " + problemId);
        }

        String snapshotJson = snapshotService.capture(problemId);
        try {
            return saveVersion(problemId, 1, "CREATE", "Initial version", operatorId, snapshotJson);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // M2 修复：兜底并发场景 — 两并发请求都通过 SELECT 查重但都尝试 INSERT,只有 1 个成功
            log.warn("Race condition: initial version INSERT collided for problem {}", problemId, e);
            throw new BusinessException(ProblemErrorCode.PROBLEM_VERSION_ALREADY_EXISTS,
                    "Initial version already exists for problem " + problemId);
        }
    }

    @Override
    @Transactional
    public ProblemVersionVO createVersion(Long problemId, String changeType, String changeSummary, String operatorId) {
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new BusinessException(ProblemErrorCode.PROBLEM_NOT_FOUND);
        }

        Integer latestVersion = problemVersionMapper.selectLatestVersionNumber(problemId);
        int versionNumber = (latestVersion != null) ? latestVersion + 1 : 1;

        String snapshotJson = snapshotService.capture(problemId);
        return saveVersion(problemId, versionNumber, changeType, changeSummary, operatorId, snapshotJson);
    }

    private ProblemVersionVO saveVersion(Long problemId, Integer versionNumber, String changeType,
                                         String changeSummary, String operatorId, String snapshotJson) {
        ProblemVersion version = new ProblemVersion();
        version.setProblemId(problemId);
        version.setVersionNumber(versionNumber);
        version.setSnapshotJson(snapshotJson);
        version.setChangeType(changeType);
        version.setChangeSummary(changeSummary);
        version.setCreatedBy(operatorId);

        problemVersionMapper.insert(version);
        return toVO(version);
    }

    private ProblemVersionVO toVO(ProblemVersion version) {
        ProblemVersionVO vo = new ProblemVersionVO();
        vo.setId(String.valueOf(version.getId()));
        vo.setVersionNumber(version.getVersionNumber());
        vo.setChangeType(version.getChangeType());
        vo.setChangeSummary(version.getChangeSummary());
        vo.setCreatedAt(version.getCreatedAt() != null ? version.getCreatedAt().toString() : null);
        vo.setCreatedBy(version.getCreatedBy());
        return vo;
    }
}
