package com.ulticode.modules.contest.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.contest.dto.*;
import com.ulticode.modules.contest.entity.ScoringRule;
import com.ulticode.modules.contest.mapper.ScoringRuleMapper;
import com.ulticode.modules.contest.service.ScoringRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScoringRuleServiceImpl implements ScoringRuleService {

    private final ScoringRuleMapper scoringRuleMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<ScoringRuleVO> findAll(boolean includeInactive) {
        List<ScoringRule> rules = includeInactive
                ? scoringRuleMapper.findAllOrdered()
                : scoringRuleMapper.findActive();
        return rules.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ScoringRuleVO findById(String id) {
        ScoringRule rule = scoringRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.SCORING_RULE_NOT_FOUND);
        }
        return toVO(rule);
    }

    @Override
    @Transactional
    public ScoringRuleVO create(CreateScoringRuleDTO dto) {
        ScoringRule rule = new ScoringRule();
        BeanUtils.copyProperties(dto, rule);
        if (rule.getIsDefault() != null && rule.getIsDefault()) {
            scoringRuleMapper.clearDefault();
        }
        if (rule.getIsActive() == null) {
            rule.setIsActive(true);
        }
        scoringRuleMapper.insert(rule);
        return toVO(rule);
    }

    @Override
    @Transactional
    public ScoringRuleVO update(String id, UpdateScoringRuleDTO dto) {
        ScoringRule rule = scoringRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.SCORING_RULE_NOT_FOUND);
        }
        BeanUtils.copyProperties(dto, rule);
        rule.setId(id);
        // Force updatedAt refresh on every PUT: MyBatis-Plus strictUpdateFill only
        // fills when the field is null, but selectById has already populated it.
        rule.setUpdatedAt(LocalDateTime.now(clock));
        if (dto.getIsDefault() != null && dto.getIsDefault()) {
            scoringRuleMapper.clearDefault();
        }
        scoringRuleMapper.updateById(rule);
        return toVO(rule);
    }

    @Override
    @Transactional
    public void delete(String id) {
        ScoringRule rule = scoringRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.SCORING_RULE_NOT_FOUND);
        }
        long count = scoringRuleMapper.countContestsUsingRule(id);
        if (count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot delete scoring rule that is in use by contests");
        }
        scoringRuleMapper.deleteById(id);
    }

    private ScoringRuleVO toVO(ScoringRule rule) {
        ScoringRuleVO vo = new ScoringRuleVO();
        BeanUtils.copyProperties(rule, vo);
        vo.setContestCount(scoringRuleMapper.countContestsUsingRule(rule.getId()));
        return vo;
    }
}
