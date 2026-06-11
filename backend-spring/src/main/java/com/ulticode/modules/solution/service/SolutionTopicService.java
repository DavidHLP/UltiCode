package com.ulticode.modules.solution.service;

import com.ulticode.modules.solution.dto.SolutionTopicVO;

import java.util.List;

/**
 * Service interface for solution topic operations (read-only for now).
 */
public interface SolutionTopicService {

    /**
     * List all active topics ordered by sort_order, with solution counts.
     *
     * @return list of topic VOs, never null, possibly empty
     */
    List<SolutionTopicVO> listTopics();
}
