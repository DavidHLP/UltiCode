package com.ulticode.modules.contest.port.adapter;

import com.ulticode.app.api.dto.ContestAdminDTO;
import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Production adapter implementing {@link ContestAdminReadPort}.
 *
 * <p>Maps backend-app contest entities to entity-free DTOs so backend-admin
 * consumers never import contest entity or mapper classes.
 *
 * <p>P7-RELOCATE-CONTEST-001 AC #7.
 *
 * @author ulticode
 */
@Component
@Primary
@RequiredArgsConstructor
public class DefaultContestAdminReadAdapter implements ContestAdminReadPort {

    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;

    @Override
    public ContestAdminDTO selectById(String id) {
        Contest contest = contestMapper.selectById(id);
        return contest != null ? toDTO(contest) : null;
    }

    @Override
    public ContestAdminDTO selectByIdOrSlug(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        Contest contest = contestMapper.selectById(identifier);
        if (contest == null) {
            contest = contestMapper.findBySlug(identifier);
        }
        return contest != null ? toDTO(contest) : null;
    }

    @Override
    public PageResult<ContestAdminDTO> selectPage(int page, int size, String keyword, String status, String contestType) {
        return selectPage(page, size, keyword, status, contestType, null, null);
    }

    @Override
    public PageResult<ContestAdminDTO> selectPage(int page, int size, String keyword, String status,
            String contestType, String sortBy, String sortOrder) {
        Page<Contest> p = new Page<>(page, size);
        LambdaQueryWrapper<Contest> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Contest::getTitle, keyword)
                    .or().like(Contest::getSlug, keyword)
                    .or().eq(Contest::getId, keyword));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(Contest::getStatus, status);
        }
        if (contestType != null && !contestType.isBlank()) {
            wrapper.eq(Contest::getContestType, contestType);
        }
        applySort(wrapper, sortBy, sortOrder);
        Page<Contest> result = contestMapper.selectPage(p, wrapper);
        List<String> contestIds = result.getRecords().stream()
                .map(Contest::getId)
                .collect(Collectors.toList());
        Map<String, Integer> problemCounts = contestIds.isEmpty()
                ? Map.of()
                : batchProblemCounts(contestIds);
        List<ContestAdminDTO> items = result.getRecords().stream()
                .map(contest -> {
                    ContestAdminDTO dto = toDTO(contest);
                    dto.setProblemCount(problemCounts.getOrDefault(contest.getId(), 0));
                    return dto;
                })
                .collect(Collectors.toList());
        return PageResult.of(items, result.getTotal(), page, size);
    }

    /**
     * Whitelisted sort mapping; anything outside the whitelist (or a null
     * sort field) falls back to {@code createdAt DESC} — the historical
     * default. Direction accepts only {@code asc}/{@code desc}.
     */
    private static void applySort(LambdaQueryWrapper<Contest> wrapper, String sortBy, String sortOrder) {
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        switch (sortBy == null ? "" : sortBy) {
            case "title" -> order(wrapper, asc, Contest::getTitle);
            case "slug" -> order(wrapper, asc, Contest::getSlug);
            case "startTime" -> order(wrapper, asc, Contest::getStartTime);
            case "createdAt" -> order(wrapper, asc, Contest::getCreatedAt);
            case "updatedAt" -> order(wrapper, asc, Contest::getUpdatedAt);
            case "status" -> order(wrapper, asc, Contest::getStatus);
            case "registeredCount" -> order(wrapper, asc, Contest::getRegisteredCount);
            case "participantCount" -> order(wrapper, asc, Contest::getParticipantCount);
            default -> wrapper.orderByDesc(Contest::getCreatedAt);
        }
    }

    private static <T> void order(LambdaQueryWrapper<Contest> wrapper, boolean asc, SFunction<Contest, T> column) {
        if (asc) {
            wrapper.orderByAsc(column);
        } else {
            wrapper.orderByDesc(column);
        }
    }

    @Override
    public List<ContestAdminDTO> selectAll(List<String> statusNames) {
        LambdaQueryWrapper<Contest> wrapper = new LambdaQueryWrapper<>();
        if (statusNames != null && !statusNames.isEmpty()) {
            wrapper.in(Contest::getStatus, statusNames);
        }
        List<Contest> contests = contestMapper.selectList(wrapper);
        return contests.stream().map(DefaultContestAdminReadAdapter::toDTO).collect(Collectors.toList());
    }

    @Override
    public long countByStatus(String statusName) {
        LambdaQueryWrapper<Contest> wrapper = new LambdaQueryWrapper<>();
        if (statusName != null && !statusName.isBlank()) {
            wrapper.eq(Contest::getStatus, statusName);
        }
        return contestMapper.selectCount(wrapper);
    }

    @Override
    public long countProblemsByContestId(String contestId) {
        return contestProblemMapper.countByContestId(contestId);
    }
    /**
     * Single-query batch for problem counts per contest, eliminating the
     * N+1 call to {@link #countProblemsByContestId} per contest row.
     */
    private Map<String, Integer> batchProblemCounts(List<String> contestIds) {
        List<Map<String, Object>> rows = contestProblemMapper.countByContestIds(contestIds);
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (Map<String, Object> row : rows) {
            String contestId = (String) row.get("contestId");
            Number cnt = (Number) row.get("cnt");
            counts.put(contestId, cnt != null ? cnt.intValue() : 0);
        }
        return counts;
    }

    private static ContestAdminDTO toDTO(Contest contest) {
        ContestAdminDTO dto = new ContestAdminDTO();
        dto.setId(contest.getId());
        dto.setSlug(contest.getSlug());
        dto.setTitle(contest.getTitle());
        dto.setDescription(contest.getDescription());
        dto.setContestType(contest.getContestType());
        dto.setScoringMode(contest.getScoringMode());
        dto.setStatus(contest.getStatus());
        dto.setCreatorUserId(contest.getCreatedBy());
        dto.setStartTime(contest.getStartTime());
        dto.setEndTime(contest.getEndTime());
        dto.setActualStartTime(contest.getActualStartTime());
        dto.setActualEndTime(contest.getActualEndTime());
        dto.setDurationMinutes(contest.getDurationMinutes());
        dto.setMaxParticipants(contest.getMaxParticipants());
        dto.setRegisteredCount(contest.getRegisteredCount());
        dto.setIsVisible(contest.getIsVisible());
        dto.setIsDeleted(contest.getIsDeleted());
        dto.setIsRated(contest.getIsRated());
        dto.setCreatedAt(contest.getCreatedAt());
        dto.setUpdatedAt(contest.getUpdatedAt());
        return dto;
    }

    @Override
    public List<ContestAdminDTO> selectByStartTimeAfter(java.time.LocalDateTime afterStartTime) {
        LambdaQueryWrapper<Contest> wrapper = new LambdaQueryWrapper<>();
        if (afterStartTime != null) {
            wrapper.ge(Contest::getStartTime, afterStartTime);
        }
        wrapper.orderByDesc(Contest::getCreatedAt);
        List<Contest> contests = contestMapper.selectList(wrapper);
        return contests.stream().map(DefaultContestAdminReadAdapter::toDTO).collect(Collectors.toList());
    }
}
