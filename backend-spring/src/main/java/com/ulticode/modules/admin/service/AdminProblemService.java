package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.problem.*;

/**
 * Admin service for problem management with tab-specific data.
 */
public interface AdminProblemService {

    /**
     * Get header data for problem header tab.
     *
     * @param id Problem ID
     * @return Header data
     */
    HeaderDataVO getHeaderData(Long id);

    /**
     * Get description data for problem description tab.
     *
     * @param id Problem ID
     * @return Description data with details, examples, constraints, and tags
     */
    DescriptionDataVO getDescriptionData(Long id);

    /**
     * Get code data for problem code tab.
     *
     * @param id Problem ID
     * @return Code data with language starter codes
     */
    CodeDataVO getCodeData(Long id);

    /**
     * Get cases data for problem test cases tab.
     *
     * @param id Problem ID
     * @return Cases data with examples, constraints, and hints
     */
    CasesDataVO getCasesData(Long id);
}
