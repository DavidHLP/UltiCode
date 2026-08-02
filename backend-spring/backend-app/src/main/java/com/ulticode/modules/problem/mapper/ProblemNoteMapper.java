package com.ulticode.modules.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.problem.entity.ProblemNote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * Mapper for problem notes.
 *
 * @author Claude
 * @since 2026-06-11
 */
@Mapper
public interface ProblemNoteMapper extends BaseMapper<ProblemNote> {

    /**
     * Find a user's note for a specific problem.
     *
     * @param userId    the user ID
     * @param problemId the problem ID
     * @return the note if present
     */
    @Select("SELECT * FROM problem_notes WHERE user_id = #{userId} AND problem_id = #{problemId} LIMIT 1")
    Optional<ProblemNote> findByUserAndProblem(@Param("userId") String userId,
                                                 @Param("problemId") Long problemId);
}
