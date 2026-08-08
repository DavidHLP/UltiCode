package com.ulticode.modules.contest.service;

import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
import com.ulticode.modules.contest.entity.Contest;

import java.util.Optional;

public interface ContestAdministrationDomainService {
    Optional<Contest> findById(String id);
    Optional<Contest> findBySlug(String slug);
    Contest createContest(CreateContestDTO dto, String creatorAccountId);
    Contest updateContest(String id, UpdateContestDTO dto, String actorId);
    void deleteContest(String id, String actorId);
    Contest startContest(String id, String actorId);
    Contest endContest(String id, String actorId);
}
