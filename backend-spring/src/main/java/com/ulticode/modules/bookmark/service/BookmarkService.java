package com.ulticode.modules.bookmark.service;

import com.ulticode.modules.bookmark.dto.*;
import com.ulticode.modules.bookmark.entity.enums.BookmarkType;

import java.util.List;

/**
 * Service interface for bookmark operations.
 */
public interface BookmarkService {

    /**
     * Quick favorite/unfavorite an item.
     * Adds to default folder if not favorited, removes from all folders if favorited.
     *
     * @param userId     the user ID
     * @param dto        the quick favorite DTO
     * @return result with favorite status and folder IDs
     */
    QuickFavoriteVO quickFavorite(String userId, QuickFavoriteDTO dto);

    /**
     * Get all folders for a user.
     *
     * @param userId the user ID
     * @return list of folders with item counts
     */
    List<BookmarkFolderVO> getFolders(String userId);

    /**
     * Get a folder with its bookmarks.
     *
     * @param userId   the user ID
     * @param folderId the folder ID
     * @return folder with bookmarks
     */
    BookmarkFolderDetailVO getFolderDetail(String userId, String folderId);

    /**
     * Create a new folder.
     *
     * @param userId the user ID
     * @param dto    the create folder DTO
     * @return the created folder
     */
    BookmarkFolderVO createFolder(String userId, CreateFolderDTO dto);

    /**
     * Update a folder.
     *
     * @param userId   the user ID
     * @param folderId the folder ID
     * @param dto      the update folder DTO
     * @return the updated folder
     */
    BookmarkFolderVO updateFolder(String userId, String folderId, UpdateFolderDTO dto);

    /**
     * Delete a folder.
     *
     * @param userId   the user ID
     * @param folderId the folder ID
     */
    void deleteFolder(String userId, String folderId);

    /**
     * Add a bookmark to a folder.
     *
     * @param userId   the user ID
     * @param folderId the folder ID
     * @param dto      the add bookmark DTO
     * @return the created bookmark
     */
    BookmarkVO addBookmark(String userId, String folderId, AddBookmarkDTO dto);

    /**
     * Remove a bookmark from a folder by bookmark ID.
     *
     * @param userId     the user ID
     * @param folderId   the folder ID
     * @param bookmarkId the bookmark ID
     */
    void removeBookmark(String userId, String folderId, String bookmarkId);

    /**
     * Remove a bookmark from a folder by target.
     *
     * @param userId     the user ID
     * @param folderId   the folder ID
     * @param targetType the target type
     * @param targetId   the target ID
     */
    void removeBookmarkByTarget(String userId, String folderId, BookmarkType targetType, String targetId);

    /**
     * Update a bookmark.
     *
     * @param userId     the user ID
     * @param folderId   the folder ID
     * @param bookmarkId the bookmark ID
     * @param dto        the update bookmark DTO
     * @return the updated bookmark
     */
    BookmarkVO updateBookmark(String userId, String folderId, String bookmarkId, UpdateBookmarkDTO dto);

    /**
     * Get folders containing a specific item.
     *
     * @param userId     the user ID
     * @param targetType the target type
     * @param targetId   the target ID
     * @return item with its folders
     */
    ItemFoldersVO getItemFolders(String userId, BookmarkType targetType, String targetId);

    /**
     * Reorder folders.
     *
     * @param userId    the user ID
     * @param folderIds the ordered list of folder IDs
     */
    void reorderFolders(String userId, List<String> folderIds);
}
