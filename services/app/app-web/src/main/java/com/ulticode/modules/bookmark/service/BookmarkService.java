package com.ulticode.modules.bookmark.service;

import com.ulticode.modules.bookmark.dto.*;
import com.ulticode.modules.bookmark.entity.enums.BookmarkType;

import java.util.List;

/**
 * Bookmark collection service.
 *
 * <p>The module's DEPTH is the set of collection invariants it owns
 * internally — folder ownership, default-folder existence, item ordering,
 * and duplicate-convergence under the {@code collection_items} unique index.
 * Callers express collection intent (favorite toggle, add to a collection,
 * remove, reorder, read); they never choreograph those invariants.
 *
 * <p>Persistence stays behind the mapper adapters in
 * {@code com.ulticode.modules.bookmark.mapper}; this interface never
 * exposes entity types or storage verbs.
 */
public interface BookmarkService {

    /**
     * Toggle whether a target is in the user's collection.
     *
     * <p>If the target is absent from every folder the user owns, it is added
     * to the user's default folder (creating that folder first if needed,
     * assigning the next sort order, and converging on the current state if a
     * concurrent insert trips the unique index). If the target is already in
     * one or more folders, it is removed from all of them.
     *
     * @param userId the owning user
     * @param dto    the target to toggle
     * @return the resulting favorite state and the folders involved
     */
    QuickFavoriteVO quickFavorite(String userId, QuickFavoriteDTO dto);

    /**
     * List the user's folders with per-folder item counts.
     *
     * @param userId the owning user
     * @return folders in display order, each with its item count
     */
    List<BookmarkFolderVO> getFolders(String userId);

    /**
     * Read a single folder together with its items.
     *
     * @param userId   the owning user
     * @param folderId the folder to read
     * @return the folder and its items
     */
    BookmarkFolderDetailVO getFolderDetail(String userId, String folderId);

    /**
     * Create a named folder for the user.
     *
     * <p>Ensures the user has a default folder (creating one if absent) and
     * assigns the new folder the next display sort order. Rejects a name
     * already in use by another of the user's folders.
     *
     * @param userId the owning user
     * @param dto    the folder to create
     * @return the created folder
     */
    BookmarkFolderVO createFolder(String userId, CreateFolderDTO dto);

    /**
     * Update a folder's editable fields.
     *
     * <p>Rejects a rename that collides with another of the user's folders.
     *
     * @param userId   the owning user
     * @param folderId the folder to update
     * @param dto      the fields to update
     * @return the updated folder
     */
    BookmarkFolderVO updateFolder(String userId, String folderId, UpdateFolderDTO dto);

    /**
     * Delete a non-default folder and every item it contains.
     *
     * <p>The user's default folder cannot be deleted.
     *
     * @param userId   the owning user
     * @param folderId the folder to delete
     */
    void deleteFolder(String userId, String folderId);

    /**
     * Add a target to a folder, converging on the existing row if present.
     *
     * <p>If the target is already in the folder, the existing item is
     * returned unchanged (idempotent add). Otherwise a new item is inserted
     * at the next sort order, converging on the current state if a concurrent
     * insert trips the unique index.
     *
     * @param userId   the owning user
     * @param folderId the destination folder
     * @param dto      the target to add
     * @return the added (or pre-existing) item
     */
    BookmarkVO addBookmark(String userId, String folderId, AddBookmarkDTO dto);

    /**
     * Remove an item from a folder by item ID.
     *
     * @param userId     the owning user
     * @param folderId   the folder
     * @param bookmarkId the item to remove
     */
    void removeBookmark(String userId, String folderId, String bookmarkId);

    /**
     * Remove an item from a folder by target.
     *
     * @param userId     the owning user
     * @param folderId   the folder
     * @param targetType the target type
     * @param targetId   the target id
     */
    void removeBookmarkByTarget(String userId, String folderId, BookmarkType targetType, String targetId);

    /**
     * Update an item's mutable fields (note, sort order).
     *
     * @param userId     the owning user
     * @param folderId   the folder
     * @param bookmarkId the item to update
     * @param dto        the fields to update
     * @return the updated item
     */
    BookmarkVO updateBookmark(String userId, String folderId, String bookmarkId, UpdateBookmarkDTO dto);

    /**
     * Read the folders that contain a given target for the user.
     *
     * @param userId     the owning user
     * @param targetType the target type
     * @param targetId   the target id
     * @return the target together with its containing folders
     */
    ItemFoldersVO getItemFolders(String userId, BookmarkType targetType, String targetId);

    /**
     * Reorder the user's folders to match the given display order.
     *
     * @param userId    the owning user
     * @param folderIds the folder ids in the desired display order
     */
    void reorderFolders(String userId, List<String> folderIds);
}
