package com.ulticode.modules.bookmark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.bookmark.entity.Bookmark;
import com.ulticode.modules.bookmark.projection.FolderItemCount;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * Mapper for bookmark operations.
 */
@Mapper
public interface BookmarkMapper extends BaseMapper<Bookmark> {

    /**
     * Find all bookmarks in a folder, ordered by sort_order.
     *
     * @param folderId the folder ID
     * @return list of bookmarks
     */
    @Select("SELECT * FROM collection_items WHERE collection_id = #{folderId} ORDER BY sort_order ASC, created_at ASC")
    List<Bookmark> findByFolderId(@Param("folderId") String folderId);

    /**
     * Find a bookmark by folder, target type, and target ID.
     *
     * @param folderId   the folder ID
     * @param targetType the target type
     * @param targetId   the target ID
     * @return the bookmark if found
     */
    @Select("SELECT * FROM collection_items WHERE collection_id = #{folderId} AND target_type = #{targetType} AND target_id = #{targetId}")
    Optional<Bookmark> findByFolderAndTarget(@Param("folderId") String folderId,
                                              @Param("targetType") String targetType,
                                              @Param("targetId") String targetId);

    /**
     * Find all bookmarks for a target across all user's folders.
     *
     * @param userId     the user ID
     * @param targetType the target type
     * @param targetId   the target ID
     * @return list of bookmarks
     */
    @Select("SELECT ci.* FROM collection_items ci " +
            "JOIN collections c ON ci.collection_id = c.id " +
            "WHERE c.user_id = #{userId} AND ci.target_type = #{targetType} AND ci.target_id = #{targetId}")
    List<Bookmark> findByUserIdAndTarget(@Param("userId") String userId,
                                          @Param("targetType") String targetType,
                                          @Param("targetId") String targetId);

    /**
     * Find folders containing a specific target for a user.
     *
     * @param userId     the user ID
     * @param targetType the target type
     * @param targetId   the target ID
     * @return list of folder IDs
     */
    @Select("SELECT c.id FROM collections c " +
            "JOIN collection_items ci ON c.id = ci.collection_id " +
            "WHERE c.user_id = #{userId} AND ci.target_type = #{targetType} AND ci.target_id = #{targetId}")
    List<String> findFolderIdsByTarget(@Param("userId") String userId,
                                        @Param("targetType") String targetType,
                                        @Param("targetId") String targetId);

    /**
     * Count bookmarks in a folder.
     *
     * @param folderId the folder ID
     * @return number of bookmarks
     */
    @Select("SELECT COUNT(*) FROM collection_items WHERE collection_id = #{folderId}")
    long countByFolderId(@Param("folderId") String folderId);

    /**
     * Count bookmarks for each folder in a set, in a single query.
     *
     * <p>Owns the per-folder count projection so the service does not loop
     * {@link #countByFolderId(String)} once per folder (N+1 read choreography).
     * Folders with no bookmarks are absent from the result; callers treat a
     * missing folder as a count of {@code 0}.
     *
     * @param folderIds the folder IDs to project (must be non-empty)
     * @return folder-id to item-count pairs
     */
    @Select("<script>SELECT collection_id AS folderId, COUNT(*) AS itemCount " +
            "FROM collection_items WHERE collection_id IN " +
            "<foreach collection='folderIds' item='fid' open='(' separator=',' close=')'>#{fid}</foreach> " +
            "GROUP BY collection_id</script>")
    List<FolderItemCount> countItemsByFolderIds(@Param("folderIds") List<String> folderIds);

    /**
     * Get the maximum sort order in a folder.
     *
     * @param folderId the folder ID
     * @return the maximum sort order, or null if no bookmarks
     */
    @Select("SELECT MAX(sort_order) FROM collection_items WHERE collection_id = #{folderId}")
    Integer getMaxSortOrder(@Param("folderId") String folderId);

    /**
     * Delete a bookmark by folder and target.
     *
     * <p>NOTE: Must use {@link Delete} (not {@link Select}). Using {@link Select} with
     * a DELETE statement causes MyBatis to return {@code null} for the affected row
     * count, which then triggers a {@link org.apache.ibatis.binding.BindingException}
     * when auto-unboxing into the primitive {@code int} return type.
     *
     * @param folderId   the folder ID
     * @param targetType the target type
     * @param targetId   the target ID
     * @return number of rows deleted
     */
    @Delete("DELETE FROM collection_items WHERE collection_id = #{folderId} AND target_type = #{targetType} AND target_id = #{targetId}")
    int deleteByFolderAndTarget(@Param("folderId") String folderId,
                                 @Param("targetType") String targetType,
                                 @Param("targetId") String targetId);
}
