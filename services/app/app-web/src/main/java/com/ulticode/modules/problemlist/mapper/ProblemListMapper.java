package com.ulticode.modules.problemlist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.problemlist.entity.ProblemList;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus mapper for ProblemList entity.
 * Provides standard CRUD operations through BaseMapper.
 */
@Mapper
public interface ProblemListMapper extends BaseMapper<ProblemList> {

    /**
     * Find all public problem lists.
     *
     * @return list of public problem lists
     */
    @Select("SELECT * FROM problem_lists WHERE is_public = true ORDER BY created_at DESC")
    List<ProblemList> findAllPublic();

    /**
     * Find all problem lists by author ID.
     *
     * @param authorId the author ID
     * @return list of problem lists
     */
    @Select("SELECT * FROM problem_lists WHERE author_id = #{authorId} ORDER BY created_at DESC")
    List<ProblemList> findByAuthorId(@Param("authorId") String authorId);

    /**
     * Find all featured problem lists.
     *
     * @return list of featured problem lists
     */
    @Select("SELECT * FROM problem_lists WHERE is_featured = true ORDER BY banner_order ASC, created_at DESC")
    List<ProblemList> findFeatured();

    /**
     * Find a problem list by ID.
     *
     * @param id the list ID
     * @return the problem list if found
     */
    @Select("SELECT * FROM problem_lists WHERE id = #{id}")
    Optional<ProblemList> findById(@Param("id") String id);

    /**
     * Count problem lists by author ID.
     *
     * @param authorId the author ID
     * @return number of problem lists
     */
    @Select("SELECT COUNT(*) FROM problem_lists WHERE author_id = #{authorId}")
    long countByAuthorId(@Param("authorId") String authorId);
}
