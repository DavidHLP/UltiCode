package com.ulticode.modules.admin.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminContestQueryDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;

/**
 * Service interface for admin contest operations.
 */
public interface AdminContestService {

    /**
     * Get paginated list of contests with filters.
     *
     * @param query the query parameters
     * @return paginated list of contests
     */
    PageResult<AdminContestVO> getContests(AdminContestQueryDTO query);

    /**
     * Get a contest by ID.
     *
     * @param id the contest ID
     * @return the contest VO
     */
    AdminContestVO getContest(String id);
}
