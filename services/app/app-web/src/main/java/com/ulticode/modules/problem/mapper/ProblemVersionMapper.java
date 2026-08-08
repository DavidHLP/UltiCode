package com.ulticode.modules.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.problem.entity.ProblemVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis-Plus mapper for ProblemVersion entity.
 * Provides annotation-based SQL for version queries.
 */
@Mapper
public interface ProblemVersionMapper extends BaseMapper<ProblemVersion> {

    /**
     * Paginated query of problem versions by problem ID, ordered by version number descending.
     *
     * @param problemId problem identifier
     * @param page pagination page
     * @return page of ProblemVersion records
     */
    @Select("SELECT * FROM problem_versions " +
            "WHERE problem_id = #{problemId} " +
            "ORDER BY version_number DESC")
    Page<ProblemVersion> selectByProblemId(@Param("problemId") Long problemId, Page<ProblemVersion> page);

    /**
     * Query the maximum version number for a problem.
     *
     * @param problemId problem identifier
     * @return maximum version number, or null if no versions exist
     */
    @Select("SELECT MAX(version_number) FROM problem_versions WHERE problem_id = #{problemId}")
    Integer selectLatestVersionNumber(@Param("problemId") Long problemId);
}