package com.ulticode.modules.problemlist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.problemlist.entity.ProblemListBookmark;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus mapper for ProblemListBookmark entity.
 *
 * NOTE: This mapper requires the problem_list_bookmarks table to exist.
 * See ProblemListBookmark entity for migration SQL.
 */
@Mapper
public interface ProblemListBookmarkMapper extends BaseMapper<ProblemListBookmark> {

    /**
     * Find all bookmarks for a user.
     *
     * @param userId the user ID
     * @return list of bookmarks
     */
    @Select("SELECT * FROM problem_list_bookmarks WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<ProblemListBookmark> findByUserId(@Param("userId") String userId);

    /**
     * Find all bookmarks in a category.
     *
     * @param categoryId the category ID
     * @return list of bookmarks
     */
    @Select("SELECT * FROM problem_list_bookmarks WHERE category_id = #{categoryId} ORDER BY created_at DESC")
    List<ProblemListBookmark> findByCategoryId(@Param("categoryId") String categoryId);

    /**
     * Find a bookmark by user and list.
     *
     * @param userId the user ID
     * @param listId the list ID
     * @return the bookmark if found
     */
    @Select("SELECT * FROM problem_list_bookmarks WHERE user_id = #{userId} AND list_id = #{listId}")
    Optional<ProblemListBookmark> findByUserIdAndListId(
            @Param("userId") String userId,
            @Param("listId") String listId);

    /**
     * Delete a bookmark by user and list.
     *
     * @param userId the user ID
     * @param listId the list ID
     * @return number of rows deleted
     */
    @Delete("DELETE FROM problem_list_bookmarks WHERE user_id = #{userId} AND list_id = #{listId}")
    int deleteByUserIdAndListId(@Param("userId") String userId, @Param("listId") String listId);

    /**
     * Delete all bookmarks in a category.
     *
     * @param categoryId the category ID
     * @return number of rows deleted
     */
    @Delete("DELETE FROM problem_list_bookmarks WHERE category_id = #{categoryId}")
    int deleteByCategoryId(@Param("categoryId") String categoryId);

    /**
     * Count bookmarks for a user.
     *
     * @param userId the user ID
     * @return number of bookmarks
     */
    @Select("SELECT COUNT(*) FROM problem_list_bookmarks WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") String userId);

    /**
     * Check if a user has saved a list.
     *
     * @param userId the user ID
     * @param listId the list ID
     * @return true if the list is saved
     */
    @Select("SELECT COUNT(*) > 0 FROM problem_list_bookmarks WHERE user_id = #{userId} AND list_id = #{listId}")
    boolean existsByUserIdAndListId(@Param("userId") String userId, @Param("listId") String listId);
}
