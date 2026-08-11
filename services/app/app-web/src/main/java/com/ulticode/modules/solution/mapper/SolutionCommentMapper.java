package com.ulticode.modules.solution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.solution.entity.SolutionComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * MyBatis Mapper for SolutionComment entity.
 */
@Mapper
public interface SolutionCommentMapper extends BaseMapper<SolutionComment> {

    @Select("SELECT COUNT(*) FROM solution_comments WHERE solution_id = #{solutionId} AND is_deleted = 0")
    long countBySolutionId(@Param("solutionId") String solutionId);

    /**
     * Select comment by ID ignoring logical delete (for admin queries).
     */
    @Select("SELECT * FROM solution_comments WHERE id = #{id}")
    SolutionComment selectByIdIgnoreDeleted(@Param("id") String id);

    /**
     * Admin paginated query ignoring logical delete. Supports dynamic filtering.
     */
    @Select("""
            <script>
            SELECT * FROM solution_comments
            WHERE 1=1
            <if test="isFlagged != null">AND is_flagged = #{isFlagged}</if>
            <if test="isDeleted != null">AND is_deleted = #{isDeleted}</if>
            <if test="search != null and search != ''">AND content LIKE CONCAT('%', #{search}, '%')</if>
            <if test="parentEntityId != null and parentEntityId != ''">AND solution_id = #{parentEntityId}</if>
            ORDER BY
            <choose>
                <when test="sortBy == 'updatedAt'">updated_at</when>
                <otherwise>created_at</otherwise>
            </choose>
            <choose>
                <when test="sortOrder == 'asc'">ASC</when>
                <otherwise>DESC</otherwise>
            </choose>
            </script>
            """)
    List<SolutionComment> selectPageIgnoreDeleted(Page<SolutionComment> page,
                                                   @Param("isFlagged") Boolean isFlagged,
                                                   @Param("isDeleted") Boolean isDeleted,
                                                   @Param("search") String search,
                                                   @Param("parentEntityId") String parentEntityId,
                                                   @Param("sortBy") String sortBy,
                                                   @Param("sortOrder") String sortOrder);

    @Update("UPDATE solution_comments SET is_flagged = #{isFlagged}, flagged_reason = #{reason}, flagged_at = CASE WHEN #{isFlagged} = true THEN NOW() ELSE NULL END WHERE id = #{id}")
    int updateFlagStatus(@Param("id") String id, @Param("isFlagged") boolean isFlagged, @Param("reason") String reason);
}
