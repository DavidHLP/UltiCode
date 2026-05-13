package com.ulticode.modules.contest.service;

import com.ulticode.modules.contest.dto.*;

import java.util.List;

public interface ScoringRuleService {

    List<ScoringRuleVO> findAll(boolean includeInactive);

    ScoringRuleVO findById(String id);

    ScoringRuleVO create(CreateScoringRuleDTO dto);

    ScoringRuleVO update(String id, UpdateScoringRuleDTO dto);

    void delete(String id);
}
