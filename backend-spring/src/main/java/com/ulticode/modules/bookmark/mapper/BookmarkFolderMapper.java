package com.ulticode.modules.bookmark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.bookmark.entity.BookmarkFolder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * Mapper for bookmark folder operations.
 */
@Mapper
public interface BookmarkFolderMapper extends BaseMapper<BookmarkFolder> {

    /**
     * Find all folders for a user, ordered by sort_order.
     *
     * @param userId the user ID
     * @return list of folders
     */
    @Select("SELECT * FROM collections WHERE user_id = #{userId} ORDER BY sort_order ASC, created_at ASC")
    List<BookmarkFolder> findByUserId(@Param("userId") String userId);

    /**
     * Find a folder by user ID and name.
     *
     * @param userId the user ID
     * @param name   the folder name
     * @return the folder if found
     */
    @Select("SELECT * FROM collections WHERE user_id = #{userId} AND name = #{name}")
    Optional<BookmarkFolder> findByUserIdAndName(@Param("userId") String userId, @Param("name") String name);

    /**
     * Find the default folder for a user.
     *
     * @param userId the user ID
     * @return the default folder if found
     */
    @Select("SELECT * FROM collections WHERE user_id = #{userId} AND is_default = true LIMIT 1")
    Optional<BookmarkFolder> findDefaultByUserId(@Param("userId") String userId);

    /**
     * Count folders for a user.
     *
     * @param userId the user ID
     * @return number of folders
     */
    @Select("SELECT COUNT(*) FROM collections WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") String userId);

    /**
     * Get the maximum sort order for a user's folders.
     *
     * @param userId the user ID
     * @return the maximum sort order, or null if no folders
     */
    @Select("SELECT MAX(sort_order) FROM collections WHERE user_id = #{userId}")
    Integer getMaxSortOrder(@Param("userId") String userId);
}
