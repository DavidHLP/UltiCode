package com.ulticode.modules.problemlist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.problemlist.entity.ProblemListCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus mapper for ProblemListCategory entity.
 *
 * NOTE: This mapper requires the problem_list_categories table to exist.
 * See ProblemListCategory entity for migration SQL.
 */
@Mapper
public interface ProblemListCategoryMapper extends BaseMapper<ProblemListCategory> {

    /**
     * Find all categories for a user.
     *
     * @param userId the user ID
     * @return list of categories
     */
    @Select("SELECT * FROM problem_list_categories WHERE user_id = #{userId} ORDER BY sort_order ASC, created_at ASC")
    List<ProblemListCategory> findByUserId(@Param("userId") String userId);

    /**
     * Find a category by ID.
     *
     * @param id the category ID
     * @return the category if found
     */
    @Select("SELECT * FROM problem_list_categories WHERE id = #{id}")
    Optional<ProblemListCategory> findById(@Param("id") String id);

    /**
     * Find a category by user ID and name.
     *
     * @param userId the user ID
     * @param name   the category name
     * @return the category if found
     */
    @Select("SELECT * FROM problem_list_categories WHERE user_id = #{userId} AND name = #{name}")
    Optional<ProblemListCategory> findByUserIdAndName(
            @Param("userId") String userId,
            @Param("name") String name);

    /**
     * Count categories for a user.
     *
     * @param userId the user ID
     * @return number of categories
     */
    @Select("SELECT COUNT(*) FROM problem_list_categories WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") String userId);

    /**
     * Get the maximum sort order for a user's categories.
     *
     * @param userId the user ID
     * @return the maximum sort order, or null if no categories
     */
    @Select("SELECT MAX(sort_order) FROM problem_list_categories WHERE user_id = #{userId}")
    Integer getMaxSortOrder(@Param("userId") String userId);
}
