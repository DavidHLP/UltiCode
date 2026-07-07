package com.ulticode.modules.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemDetail;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.entity.ProblemLanguage;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import com.ulticode.modules.problem.entity.ProblemVersion;
import com.ulticode.modules.problem.mapper.ProblemDetailMapper;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.ProblemLanguageMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.problem.mapper.ProblemVersionMapper;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemVersionServiceImpl implements ProblemVersionService {

    private final ProblemMapper problemMapper;
    private final ProblemDetailMapper problemDetailMapper;
    private final ProblemExampleMapper problemExampleMapper;
    private final ProblemLanguageMapper problemLanguageMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final ProblemVersionMapper problemVersionMapper;
    private final ObjectMapper objectMapper;

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
            throw new BusinessException(ErrorCode.NOT_FOUND, "Version not found");
        }

        ProblemVersionDetailVO detailVO = new ProblemVersionDetailVO();
        detailVO.setId(String.valueOf(version.getId()));
        detailVO.setVersionNumber(version.getVersionNumber());
        detailVO.setChangeType(version.getChangeType());
        detailVO.setChangeSummary(version.getChangeSummary());
        detailVO.setCreatedAt(version.getCreatedAt() != null ? version.getCreatedAt().toString() : null);
        detailVO.setCreatedBy(version.getCreatedBy());

        if (version.getSnapshotJson() != null && !version.getSnapshotJson().isBlank()) {
            try {
                Map<String, Object> snapshot = objectMapper.readValue(
                        version.getSnapshotJson(), new TypeReference<Map<String, Object>>() {});
                detailVO.setTitle((String) snapshot.get("title"));
                detailVO.setSlug((String) snapshot.get("slug"));
                detailVO.setDifficulty((String) snapshot.get("difficulty"));
                detailVO.setIsPremium((Boolean) snapshot.get("isPremium"));
                detailVO.setIsPublished((Boolean) snapshot.get("isPublished"));
                detailVO.setSummary((String) snapshot.get("summary"));
                detailVO.setContent((String) snapshot.get("content"));
                detailVO.setConstraints((List<String>) snapshot.get("constraints"));
                detailVO.setHints((List<String>) snapshot.get("hints"));
                detailVO.setExamples((List<Map<String, Object>>) snapshot.get("examples"));
                detailVO.setLanguages((List<Map<String, Object>>) snapshot.get("languages"));
                detailVO.setTags((List<String>) snapshot.get("tags"));
            } catch (JsonProcessingException e) {
                log.error("Failed to parse snapshot JSON for version {}", versionId, e);
                throw new BusinessException(ErrorCode.UNKNOWN_ERROR, "Failed to parse version snapshot");
            }
        }

        return detailVO;
    }

    @Override
    public VersionWithDiffVO compareVersions(Long problemId, String fromVersionId, String toVersionId) {
        Long fromId = Long.parseLong(fromVersionId);
        Long toId = Long.parseLong(toVersionId);

        ProblemVersion fromVersion = problemVersionMapper.selectById(fromId);
        ProblemVersion toVersion = problemVersionMapper.selectById(toId);

        if (fromVersion == null || !fromVersion.getProblemId().equals(problemId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "From version not found");
        }
        if (toVersion == null || !toVersion.getProblemId().equals(problemId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "To version not found");
        }

        List<VersionDiffVO> diffs = computeDiffs(fromVersion.getSnapshotJson(), toVersion.getSnapshotJson());

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
            throw new BusinessException(ErrorCode.NOT_FOUND, "Version not found");
        }

        Map<String, Object> snapshot;
        try {
            snapshot = objectMapper.readValue(
                    targetVersion.getSnapshotJson(), new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse snapshot JSON for version {}", versionId, e);
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR, "Failed to parse version snapshot");
        }

        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }

        problem.setTitle((String) snapshot.get("title"));
        problem.setSlug((String) snapshot.get("slug"));
        problem.setDifficulty((String) snapshot.get("difficulty"));
        if (snapshot.get("isPremium") != null) {
            problem.setIsPremium((Boolean) snapshot.get("isPremium"));
        }
        if (snapshot.get("isPublished") != null) {
            problem.setIsPublished((Boolean) snapshot.get("isPublished"));
        }
        problemMapper.updateById(problem);

        ProblemDetail detail = problemDetailMapper.selectOne(
                new LambdaQueryWrapper<ProblemDetail>().eq(ProblemDetail::getProblemId, problemId));
        if (detail == null) {
            detail = new ProblemDetail();
            detail.setId(UUID.randomUUID().toString());
            detail.setProblemId(problemId);
            // problem_details.slug NOT NULL — denormalize from Problem
            // Defensive null check: problems.slug is also NOT NULL in DB, so this
            // should never be null, but assert it to fail fast with a clear message.
            java.util.Objects.requireNonNull(problem.getSlug(),
                    "Problem.slug must not be null (DB constraint guarantees it, but assert defensively)");
            detail.setSlug(problem.getSlug());
            // constraints_json NOT NULL with no DB default — initialize to empty array
            detail.setConstraintsJson(ProblemDetail.EMPTY_JSON_ARRAY);
        }
        detail.setSummary((String) snapshot.get("summary"));
        detail.setFollowUp((String) snapshot.get("followUp"));

        List<String> constraints = (List<String>) snapshot.get("constraints");
        if (constraints != null) {
            try {
                detail.setConstraintsJson(objectMapper.writeValueAsString(constraints));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize constraints during rollback", e);
            }
        } else {
            detail.setConstraintsJson(null);
        }

        List<String> hints = (List<String>) snapshot.get("hints");
        if (hints != null) {
            try {
                detail.setHints(objectMapper.writeValueAsString(hints));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize hints during rollback", e);
            }
        } else {
            detail.setHints(null);
        }

        if (problemDetailMapper.selectById(detail.getId()) != null) {
            problemDetailMapper.updateById(detail);
        } else {
            problemDetailMapper.insert(detail);
        }

        problemExampleMapper.delete(
                new LambdaQueryWrapper<ProblemExample>().eq(ProblemExample::getProblemId, problemId));
        List<Map<String, Object>> exampleSnapshots = (List<Map<String, Object>>) snapshot.get("examples");
        if (exampleSnapshots != null) {
            for (int i = 0; i < exampleSnapshots.size(); i++) {
                Map<String, Object> exSnapshot = exampleSnapshots.get(i);
                ProblemExample example = new ProblemExample();
                example.setId(UUID.randomUUID().toString());
                example.setProblemId(problemId);
                example.setExampleOrder(i + 1);
                example.setInputText((String) (exSnapshot.get("input") != null
                        ? exSnapshot.get("input")
                        : exSnapshot.get("inputText")));
                example.setOutputText((String) (exSnapshot.get("output") != null
                        ? exSnapshot.get("output")
                        : exSnapshot.get("outputText")));
                example.setExplanation((String) exSnapshot.get("explanation"));
                Object inputs = exSnapshot.get("inputs");
                if (inputs != null) {
                    try {
                        example.setInputs(objectMapper.writeValueAsString(inputs));
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to serialize inputs during rollback", e);
                    }
                }
                problemExampleMapper.insert(example);
            }
        }

        problemLanguageMapper.delete(
                new LambdaQueryWrapper<ProblemLanguage>().eq(ProblemLanguage::getProblemId, problemId));
        List<Map<String, Object>> languageSnapshots = (List<Map<String, Object>>) snapshot.get("languages");
        if (languageSnapshots != null) {
            for (Map<String, Object> langSnapshot : languageSnapshots) {
                ProblemLanguage language = new ProblemLanguage();
                language.setId(UUID.randomUUID().toString());
                language.setProblemId(problemId);
                language.setLabel((String) langSnapshot.get("label"));
                language.setValue((String) langSnapshot.get("value"));
                language.setStyle((String) langSnapshot.get("style"));
                language.setStarterCode((String) langSnapshot.get("starterCode"));
                problemLanguageMapper.insert(language);
            }
        }

        problemTagRelationMapper.delete(
                new LambdaQueryWrapper<ProblemTagRelation>().eq(ProblemTagRelation::getProblemId, problemId));
        List<String> tagLabels = (List<String>) snapshot.get("tags");
        if (tagLabels != null) {
            for (String tagLabel : tagLabels) {
                ProblemTag tag = problemTagMapper.selectOne(
                        new LambdaQueryWrapper<ProblemTag>().eq(ProblemTag::getLabel, tagLabel));
                if (tag != null) {
                    ProblemTagRelation relation = new ProblemTagRelation();
                    relation.setProblemId(problemId);
                    relation.setTagId(tag.getId());
                    problemTagRelationMapper.insert(relation);
                }
            }
        }

        String changeSummary = reason != null ? reason : "Rollback to version " + targetVersion.getVersionNumber();
        return createVersion(problemId, "ROLLBACK", changeSummary, operatorId);
    }

    @Override
    @Transactional
    public ProblemVersionVO createInitialVersion(Long problemId, String operatorId) {
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }

        // Bug #3 修复：重复调用应返回业务错误而非 500 (uk_problem_version 唯一约束)
        Integer existing = problemVersionMapper.selectLatestVersionNumber(problemId);
        if (existing != null && existing >= 1) {
            log.warn("Initial version already exists for problem {} (latest versionNumber={})",
                    problemId, existing);
            throw new BusinessException(ErrorCode.PROBLEM_VERSION_ALREADY_EXISTS,
                    "Initial version already exists for problem " + problemId);
        }

        Map<String, Object> snapshot = buildSnapshot(problemId);
        try {
            return saveVersion(problemId, 1, "CREATE", "Initial version", operatorId, snapshot);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // M2 修复：兜底并发场景 — 两并发请求都通过 SELECT 查重但都尝试 INSERT,只有 1 个成功
            log.warn("Race condition: initial version INSERT collided for problem {}", problemId, e);
            throw new BusinessException(ErrorCode.PROBLEM_VERSION_ALREADY_EXISTS,
                    "Initial version already exists for problem " + problemId);
        }
    }

    @Override
    @Transactional
    public ProblemVersionVO createVersion(Long problemId, String changeType, String changeSummary, String operatorId) {
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }

        Integer latestVersion = problemVersionMapper.selectLatestVersionNumber(problemId);
        int versionNumber = (latestVersion != null) ? latestVersion + 1 : 1;

        Map<String, Object> snapshot = buildSnapshot(problemId);
        return saveVersion(problemId, versionNumber, changeType, changeSummary, operatorId, snapshot);
    }

    private Map<String, Object> buildSnapshot(Long problemId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();

        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }

        snapshot.put("title", problem.getTitle());
        snapshot.put("slug", problem.getSlug());
        snapshot.put("difficulty", problem.getDifficulty());
        snapshot.put("isPremium", problem.getIsPremium());
        snapshot.put("isPublished", problem.getIsPublished());

        ProblemDetail detail = problemDetailMapper.selectOne(
                new LambdaQueryWrapper<ProblemDetail>().eq(ProblemDetail::getProblemId, problemId));
        if (detail != null) {
            snapshot.put("summary", detail.getSummary());
            snapshot.put("followUp", detail.getFollowUp());
            snapshot.put("constraints", parseJsonArray(detail.getConstraintsJson()));
            snapshot.put("hints", parseJsonArray(detail.getHints()));
        }

        List<ProblemExample> examples = problemExampleMapper.findByProblemIdOrderByOrder(problemId);
        List<Map<String, Object>> exampleSnapshots = new ArrayList<>();
        for (ProblemExample ex : examples) {
            Map<String, Object> exMap = new LinkedHashMap<>();
            exMap.put("input", ex.getInputText());
            exMap.put("output", ex.getOutputText());
            exMap.put("explanation", ex.getExplanation());
            if (ex.getInputs() != null && !ex.getInputs().isBlank()) {
                try {
                    exMap.put("inputs", objectMapper.readValue(ex.getInputs(), new TypeReference<List<Map<String, Object>>>() {}));
                } catch (JsonProcessingException e) {
                    exMap.put("inputs", ex.getInputs());
                }
            }
            exampleSnapshots.add(exMap);
        }
        snapshot.put("examples", exampleSnapshots);

        List<ProblemLanguage> languages = problemLanguageMapper.findByProblemId(problemId);
        List<Map<String, Object>> languageSnapshots = new ArrayList<>();
        for (ProblemLanguage lang : languages) {
            Map<String, Object> langMap = new LinkedHashMap<>();
            langMap.put("label", lang.getLabel());
            langMap.put("value", lang.getValue());
            langMap.put("style", lang.getStyle());
            langMap.put("starterCode", lang.getStarterCode());
            languageSnapshots.add(langMap);
        }
        snapshot.put("languages", languageSnapshots);

        List<String> tagLabels = getTagLabels(problemId);
        snapshot.put("tags", tagLabels);

        return snapshot;
    }

    private ProblemVersionVO saveVersion(Long problemId, Integer versionNumber, String changeType,
                                         String changeSummary, String operatorId, Map<String, Object> snapshot) {
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize snapshot for problem {}", problemId, e);
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR, "Failed to serialize problem snapshot");
        }

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

    private List<VersionDiffVO> computeDiffs(String fromJson, String toJson) {
        Map<String, Object> fromSnapshot;
        Map<String, Object> toSnapshot;
        try {
            fromSnapshot = objectMapper.readValue(fromJson, new TypeReference<Map<String, Object>>() {});
            toSnapshot = objectMapper.readValue(toJson, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse snapshot JSON for diff", e);
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR, "Failed to parse version snapshots");
        }

        List<VersionDiffVO> diffs = new ArrayList<>();
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(fromSnapshot.keySet());
        allKeys.addAll(toSnapshot.keySet());

        for (String key : allKeys) {
            Object oldValue = fromSnapshot.get(key);
            Object newValue = toSnapshot.get(key);
            if (!isValueEqual(oldValue, newValue)) {
                VersionDiffVO diff = new VersionDiffVO();
                diff.setField(key);
                diff.setOldValue(oldValue);
                diff.setNewValue(newValue);
                diffs.add(diff);
            }
        }

        return diffs;
    }

    private boolean isValueEqual(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        try {
            return objectMapper.writeValueAsString(a).equals(objectMapper.writeValueAsString(b));
        } catch (JsonProcessingException e) {
            return a.equals(b);
        }
    }

    private List<String> getTagLabels(Long problemId) {
        List<ProblemMapper.ProblemTagDTO> tagDTOs = problemMapper.selectTagsByProblemIds(List.of(problemId));
        return tagDTOs.stream()
                .map(ProblemMapper.ProblemTagDTO::tagName)
                .collect(Collectors.toList());
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON array: {}", json, e);
            return Collections.emptyList();
        }
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
