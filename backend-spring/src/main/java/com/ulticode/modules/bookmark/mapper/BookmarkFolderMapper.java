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
     * Find a user's folders that contain a specific target item.
     *
     * <p>Owns the "which folders hold this item" read so the service does not
     * resolve folder IDs and then fetch each folder by primary key (N+1 read
     * choreography). Ordered consistently with {@link #findByUserId(String)}.
     *
     * @param userId     the user ID
     * @param targetType the target type
     * @param targetId   the target ID
     * @return folders containing the target, in display order
     */
    @Select("SELECT c.* FROM collections c WHERE c.user_id = #{userId} AND EXISTS (" +
            "SELECT 1 FROM collection_items ci WHERE ci.collection_id = c.id " +
            "AND ci.target_type = #{targetType} AND ci.target_id = #{targetId}) " +
            "ORDER BY c.sort_order ASC, c.created_at ASC")
    List<BookmarkFolder> findByUserAndTarget(@Param("userId") String userId,
                                              @Param("targetType") String targetType,
                                              @Param("targetId") String targetId);

    /**
     * Get the maximum sort order for a user's folders.
     *
     * @param userId the user ID
     * @return the maximum sort order, or null if no folders
     */
    @Select("SELECT MAX(sort_order) FROM collections WHERE user_id = #{userId}")
    Integer getMaxSortOrder(@Param("userId") String userId);
}
