package com.ulticode.modules.contest.port.adapter;

import com.ulticode.app.api.dto.ContestAdminDTO;
import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
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
    public PageResult<ContestAdminDTO> selectPage(int page, int size, String keyword, String status, String contestType) {
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
        wrapper.orderByDesc(Contest::getCreatedAt);
        Page<Contest> result = contestMapper.selectPage(p, wrapper);
        List<ContestAdminDTO> items = result.getRecords().stream()
                .map(DefaultContestAdminReadAdapter::toDTO)
                .collect(Collectors.toList());
        return PageResult.of(items, result.getTotal(), page, size);
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
