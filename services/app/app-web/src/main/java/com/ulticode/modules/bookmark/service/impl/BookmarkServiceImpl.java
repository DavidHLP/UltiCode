package com.ulticode.modules.bookmark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.error.BookmarkErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.modules.bookmark.dto.*;
import com.ulticode.modules.bookmark.entity.Bookmark;
import com.ulticode.modules.bookmark.entity.BookmarkFolder;
import com.ulticode.modules.bookmark.entity.enums.BookmarkType;
import com.ulticode.modules.bookmark.mapper.BookmarkFolderMapper;
import com.ulticode.modules.bookmark.mapper.BookmarkMapper;
import com.ulticode.modules.bookmark.projection.BookmarkProjection;
import com.ulticode.modules.bookmark.service.BookmarkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bookmark collection service implementation.
 *
 * <p>This is the DEEP half of the bookmark module. Every collection operation
 * delegates to a small set of named invariants that the module owns:
 * <ul>
 *   <li>{@link #requireOwnedFolder} — folder ownership</li>
 *   <li>{@link #ensureDefaultFolder} — default-folder existence</li>
 *   <li>{@link #nextItemSortOrder} / {@link #nextFolderSortOrder} — ordering</li>
 *   <li>{@link #insertItemConverging} — duplicate-key convergence under the
 *       {@code collection_items} unique index</li>
 * </ul>
 * Callers (the controller) express collection intent; the choreography of
 * those invariants never leaves this class.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkServiceImpl implements BookmarkService {

    private final BookmarkFolderMapper folderMapper;
    private final BookmarkMapper bookmarkMapper;
    private final BookmarkProjection projection;

    @Override
    @Transactional
    public QuickFavoriteVO quickFavorite(String userId, QuickFavoriteDTO dto) {
        BookmarkType targetType = dto.getTargetType();
        String targetId = dto.getTargetId();

        // Toggle off: the target is already in at least one of the user's folders.
        List<String> existingFolderIds = bookmarkMapper.findFolderIdsByTarget(userId, targetType.name(), targetId);
        if (!existingFolderIds.isEmpty()) {
            for (String folderId : existingFolderIds) {
                bookmarkMapper.deleteByFolderAndTarget(folderId, targetType.name(), targetId);
            }
            log.debug("Removed {}:{} from {} folders for user {}", targetType, targetId, existingFolderIds.size(), userId);
            return new QuickFavoriteVO(false, List.of());
        }

        // Toggle on: add to the default folder, converging on the current state
        // if a concurrent insert wins the race for the same unique row.
        BookmarkFolder defaultFolder = ensureDefaultFolder(userId);
        Bookmark bookmark = newItem(defaultFolder.getId(), targetType, targetId, null);
        List<String> folderIds = insertItemConverging(userId, bookmark, targetType, targetId);

        log.debug("Quick favorited {}:{} for user {} in folder {}", targetType, targetId, userId, defaultFolder.getId());
        return new QuickFavoriteVO(true, folderIds);
    }

    @Override
    public List<BookmarkFolderVO> getFolders(String userId) {
        return projection.listFolders(userId);
    }

    @Override
    public BookmarkFolderDetailVO getFolderDetail(String userId, String folderId) {
        BookmarkFolder folder = requireOwnedFolder(folderId, userId);
        return projection.folderDetail(folder);
    }

    @Override
    @Transactional
    public BookmarkFolderVO createFolder(String userId, CreateFolderDTO dto) {
        // Reject a name already in use by another of the user's folders.
        Optional<BookmarkFolder> existing = folderMapper.findByUserIdAndName(userId, dto.getName());
        if (existing.isPresent()) {
            throw new BusinessException(BookmarkErrorCode.BOOKMARK_FOLDER_NAME_EXISTS);
        }

        // Ensure the user has a default folder before adding a named one.
        ensureDefaultFolder(userId);

        BookmarkFolder folder = new BookmarkFolder();
        folder.setUserId(userId);
        folder.setName(dto.getName());
        folder.setDescription(dto.getDescription());
        folder.setIcon(dto.getIcon());
        folder.setColor(dto.getColor());
        folder.setSortOrder(nextFolderSortOrder(userId));
        folder.setIsDefault(false);
        folderMapper.insert(folder);

        log.debug("Created folder {} for user {}", folder.getId(), userId);
        return projection.toFolderVO(folder, 0);
    }

    @Override
    @Transactional
    public BookmarkFolderVO updateFolder(String userId, String folderId, UpdateFolderDTO dto) {
        BookmarkFolder folder = requireOwnedFolder(folderId, userId);

        // Reject a rename that collides with another of the user's folders.
        if (dto.getName() != null && !dto.getName().equals(folder.getName())) {
            Optional<BookmarkFolder> existing = folderMapper.findByUserIdAndName(userId, dto.getName());
            if (existing.isPresent()) {
                throw new BusinessException(BookmarkErrorCode.BOOKMARK_FOLDER_NAME_EXISTS);
            }
            folder.setName(dto.getName());
        }

        if (dto.getDescription() != null) {
            folder.setDescription(dto.getDescription());
        }
        if (dto.getIcon() != null) {
            folder.setIcon(dto.getIcon());
        }
        if (dto.getColor() != null) {
            folder.setColor(dto.getColor());
        }
        if (dto.getSortOrder() != null) {
            folder.setSortOrder(dto.getSortOrder());
        }

        folderMapper.updateById(folder);
        log.debug("Updated folder {} for user {}", folderId, userId);
        return projection.toFolderVO(folder, bookmarkMapper.countByFolderId(folderId));
    }

    @Override
    @Transactional
    public void deleteFolder(String userId, String folderId) {
        BookmarkFolder folder = requireOwnedFolder(folderId, userId);

        if (Boolean.TRUE.equals(folder.getIsDefault())) {
            throw new BusinessException(BookmarkErrorCode.BOOKMARK_CANNOT_DELETE_DEFAULT);
        }

        // Delete all bookmarks in the folder (cascade should handle this, but be explicit)
        LambdaQueryWrapper<Bookmark> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Bookmark::getFolderId, folderId);
        bookmarkMapper.delete(wrapper);

        // Delete the folder
        folderMapper.deleteById(folderId);
        log.debug("Deleted folder {} for user {}", folderId, userId);
    }

    @Override
    @Transactional
    public BookmarkVO addBookmark(String userId, String folderId, AddBookmarkDTO dto) {
        requireOwnedFolder(folderId, userId);

        // Idempotent add: if the target is already in the folder, return it unchanged.
        Optional<Bookmark> existing = bookmarkMapper.findByFolderAndTarget(
                folderId, dto.getTargetType().name(), dto.getTargetId());
        if (existing.isPresent()) {
            return projection.toBookmarkVO(existing.get());
        }

        Bookmark bookmark = newItem(folderId, dto.getTargetType(), dto.getTargetId(), dto.getNote());
        insertItemConverging(userId, bookmark, dto.getTargetType(), dto.getTargetId());

        log.debug("Added bookmark {}:{} to folder {} for user {}",
                dto.getTargetType(), dto.getTargetId(), folderId, userId);
        return projection.toBookmarkVO(bookmark);
    }

    @Override
    @Transactional
    public void removeBookmark(String userId, String folderId, String bookmarkId) {
        requireOwnedFolder(folderId, userId);

        Bookmark bookmark = bookmarkMapper.selectById(bookmarkId);
        if (bookmark == null || !bookmark.getFolderId().equals(folderId)) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Bookmark not found in this folder");
        }

        bookmarkMapper.deleteById(bookmarkId);
        log.debug("Removed bookmark {} from folder {} for user {}", bookmarkId, folderId, userId);
    }

    @Override
    @Transactional
    public void removeBookmarkByTarget(String userId, String folderId, BookmarkType targetType, String targetId) {
        requireOwnedFolder(folderId, userId);

        int deleted = bookmarkMapper.deleteByFolderAndTarget(folderId, targetType.name(), targetId);
        if (deleted == 0) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Bookmark not found in this folder");
        }
        log.debug("Removed {}:{} from folder {} for user {}", targetType, targetId, folderId, userId);
    }

    @Override
    @Transactional
    public BookmarkVO updateBookmark(String userId, String folderId, String bookmarkId, UpdateBookmarkDTO dto) {
        requireOwnedFolder(folderId, userId);

        Bookmark bookmark = bookmarkMapper.selectById(bookmarkId);
        if (bookmark == null || !bookmark.getFolderId().equals(folderId)) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Bookmark not found in this folder");
        }

        if (dto.getNote() != null) {
            bookmark.setNote(dto.getNote());
        }
        if (dto.getSortOrder() != null) {
            bookmark.setSortOrder(dto.getSortOrder());
        }

        bookmarkMapper.updateById(bookmark);
        log.debug("Updated bookmark {} in folder {} for user {}", bookmarkId, folderId, userId);
        return projection.toBookmarkVO(bookmark);
    }

    @Override
    public ItemFoldersVO getItemFolders(String userId, BookmarkType targetType, String targetId) {
        return projection.itemFolders(userId, targetType, targetId);
    }

    @Override
    @Transactional
    public void reorderFolders(String userId, List<String> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return;
        }
        // Load and validate ownership in a single batch read instead of one
        // selectById per folder (avoids a 2N read choreography on reorder).
        List<BookmarkFolder> folders = folderMapper.selectBatchIds(folderIds);
        Map<String, BookmarkFolder> folderById = folders.stream()
                .collect(Collectors.toMap(BookmarkFolder::getId, Function.identity()));
        for (String folderId : folderIds) {
            BookmarkFolder folder = folderById.get(folderId);
            if (folder == null || !folder.getUserId().equals(userId)) {
                throw new BusinessException(BookmarkErrorCode.BOOKMARK_FOLDER_NOT_FOUND, "Folder not found: " + folderId);
            }
        }

        // Reuse the already-loaded instances — no second fetch per folder.
        for (int i = 0; i < folderIds.size(); i++) {
            BookmarkFolder folder = folderById.get(folderIds.get(i));
            folder.setSortOrder(i);
            folderMapper.updateById(folder);
        }

        log.debug("Reordered {} folders for user {}", folderIds.size(), userId);
    }

    // ==================== Collection invariants (the module's depth) ====================

    /**
     * Folder ownership invariant. Returns the folder if {@code userId} owns it,
     * otherwise throws {@link BookmarkErrorCode#BOOKMARK_FOLDER_NOT_FOUND} (missing) or
     * {@link BaseErrorCode#FORBIDDEN} (owned by another user).
     *
     * @param folderId the folder to resolve
     * @param userId   the requesting user
     * @return the owned folder
     */
    private BookmarkFolder requireOwnedFolder(String folderId, String userId) {
        BookmarkFolder folder = folderMapper.selectById(folderId);
        if (folder == null) {
            throw new BusinessException(BookmarkErrorCode.BOOKMARK_FOLDER_NOT_FOUND);
        }
        if (!folder.getUserId().equals(userId)) {
            throw new BusinessException(BaseErrorCode.FORBIDDEN, "Cannot access other user's folder");
        }
        return folder;
    }

    /**
     * Default-folder existence invariant. Returns the user's default folder,
     * creating it (with canonical name/icon/color and sort order {@code 0}) if
     * the user does not yet have one.
     *
     * @param userId the owning user
     * @return the user's default folder
     */
    private BookmarkFolder ensureDefaultFolder(String userId) {
        return folderMapper.findDefaultByUserId(userId)
                .orElseGet(() -> createDefaultFolder(userId));
    }

    /**
     * Item-ordering invariant. Returns the next sort order for an item in the
     * given folder ({@code max + 1}, or {@code 0} for the first item).
     *
     * @param folderId the folder receiving the item
     * @return the next item sort order
     */
    private Integer nextItemSortOrder(String folderId) {
        Integer max = bookmarkMapper.getMaxSortOrder(folderId);
        return max == null ? 0 : max + 1;
    }

    /**
     * Folder-ordering invariant. Returns the next sort order for a folder
     * belonging to the user ({@code max + 1}, or {@code 1} when the user has
     * no folders yet so the default folder keeps order {@code 0}).
     *
     * @param userId the owning user
     * @return the next folder sort order
     */
    private Integer nextFolderSortOrder(String userId) {
        Integer max = folderMapper.getMaxSortOrder(userId);
        return max == null ? 1 : max + 1;
    }

    /**
     * Duplicate-convergence invariant. Inserts the item, and if a concurrent
     * insert between the pre-read and this insert trips the
     * {@code collection_items} unique index, re-reads the true current state
     * and returns it instead of surfacing the race to the caller.
     *
     * <p>Used by both {@link #quickFavorite} and {@link #addBookmark} so the
     * duplicate rule lives in exactly one place.
     *
     * @param userId     the owning user (for the re-read query)
     * @param bookmark   the item to insert (sort order already assigned)
     * @param targetType the target type (for the re-read query)
     * @param targetId   the target id (for the re-read query)
     * @return the folders now holding the target (the inserted folder, or the
     *         true current set on convergence)
     */
    private List<String> insertItemConverging(String userId, Bookmark bookmark,
                                               BookmarkType targetType, String targetId) {
        try {
            bookmarkMapper.insert(bookmark);
            return List.of(bookmark.getFolderId());
        } catch (DuplicateKeyException ex) {
            // 并发场景:在 pre-read 与 insert 之间,另一线程已经插入了
            // 相同的 (collection_id, target_type, target_id),触发 collection_items 的
            // UNIQUE 索引。收敛到「当前真实状态」并返回成功,避免对用户暴露竞态错误。
            log.warn("Converging on current state after duplicate-key collision for user={}, type={}, id={}",
                    userId, targetType, targetId);
            return bookmarkMapper.findFolderIdsByTarget(userId, targetType.name(), targetId);
        }
    }

    // ==================== Private helpers ====================

    /**
     * Build an item entity ready to insert, with the next sort order assigned.
     */
    private Bookmark newItem(String folderId, BookmarkType targetType, String targetId, String note) {
        Bookmark bookmark = new Bookmark();
        bookmark.setFolderId(folderId);
        bookmark.setTargetId(targetId);
        bookmark.setTargetType(targetType.name());
        bookmark.setNote(note);
        bookmark.setSortOrder(nextItemSortOrder(folderId));
        return bookmark;
    }

    private BookmarkFolder createDefaultFolder(String userId) {
        BookmarkFolder folder = new BookmarkFolder();
        folder.setUserId(userId);
        folder.setName("Favorites");
        folder.setDescription("Default bookmark folder");
        folder.setIcon("star");
        folder.setColor("yellow");
        folder.setSortOrder(0);
        folder.setIsDefault(true);
        folderMapper.insert(folder);
        log.debug("Created default folder for user {}", userId);
        return folder;
    }
}
