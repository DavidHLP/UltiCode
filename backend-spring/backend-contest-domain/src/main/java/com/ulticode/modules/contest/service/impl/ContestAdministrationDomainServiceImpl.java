package com.ulticode.modules.contest.service.impl;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.port.ContestWritePort;
import com.ulticode.modules.contest.service.ContestAdministrationDomainService;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.Optional;

@Slf4j
public class ContestAdministrationDomainServiceImpl implements ContestAdministrationDomainService {

    private final ContestWritePort writePort;
    private final Clock clock;

    public ContestAdministrationDomainServiceImpl(ContestWritePort writePort, Clock clock) {
        this.writePort = writePort;
        this.clock = clock;
    }

    @Override
    public Optional<Contest> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(writePort.selectById(id));
    }

    @Override
    public Optional<Contest> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(writePort.selectBySlug(slug));
    }

    @Override
    public Contest createContest(CreateContestDTO dto, String creatorAccountId) {
        if (findBySlug(dto.getSlug()).isPresent()) {
            throw new BusinessException(BaseErrorCode.CONFLICT, "Contest with this slug already exists");
        }

        Contest contest = new Contest();
        contest.setSlug(dto.getSlug());
        contest.setTitle(dto.getTitle());
        contest.setContestType(dto.getContestType());
        contest.setScoringRuleId(dto.getScoringRuleId());
        contest.setDescription(dto.getDescription());
        contest.setStartTime(dto.getStartTime());
        contest.setDurationMinutes(dto.getDuration());
        if (dto.getStartTime() != null && dto.getDuration() != null) {
            contest.setEndTime(dto.getStartTime().plusMinutes(dto.getDuration()));
        }
        contest.setStatus(ContestStatus.UPCOMING.name());
        contest.setIsDeleted(false);

        writePort.insert(contest);
        log.info("Contest created: {} by user {}", contest.getId(), creatorAccountId);
        return contest;
    }

    @Override
    public Contest updateContest(String id, UpdateContestDTO dto, String actorId) {
        Contest contest = findById(id)
                .orElseThrow(() -> new BusinessException(BaseErrorCode.NOT_FOUND, "Contest not found"));

        if (dto.getTitle() != null) {
            contest.setTitle(dto.getTitle());
        }
        if (dto.getStartTime() != null) {
            contest.setStartTime(dto.getStartTime());
        }
        if (dto.getDuration() != null) {
            contest.setDurationMinutes(dto.getDuration());
        }
        if (contest.getStartTime() != null && contest.getDurationMinutes() != null) {
            contest.setEndTime(contest.getStartTime().plusMinutes(contest.getDurationMinutes()));
        }

        writePort.updateById(contest);
        log.info("Contest updated: {} by user {}", id, actorId);
        return contest;
    }

    @Override
    public void deleteContest(String id, String actorId) {
        Contest contest = findById(id)
                .orElseThrow(() -> new BusinessException(BaseErrorCode.NOT_FOUND, "Contest not found"));

        writePort.deleteById(id);
        log.info("Contest deleted: {} by user {}", id, actorId);
    }

    @Override
    public Contest startContest(String id, String actorId) {
        Contest contest = findById(id)
                .orElseThrow(() -> new BusinessException(BaseErrorCode.NOT_FOUND, "Contest not found"));

        contest.setStatus(ContestStatus.RUNNING.name());
        writePort.updateById(contest);
        log.info("Contest started: {} by user {}", id, actorId);
        return contest;
    }

    @Override
    public Contest endContest(String id, String actorId) {
        Contest contest = findById(id)
                .orElseThrow(() -> new BusinessException(BaseErrorCode.NOT_FOUND, "Contest not found"));

        contest.setStatus(ContestStatus.FINISHED.name());
        writePort.updateById(contest);
        log.info("Contest ended: {} by user {}", id, actorId);
        return contest;
    }
}
