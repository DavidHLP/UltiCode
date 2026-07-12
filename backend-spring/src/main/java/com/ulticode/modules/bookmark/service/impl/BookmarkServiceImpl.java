package com.ulticode.modules.bookmark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.bookmark.dto.*;
import com.ulticode.modules.bookmark.entity.Bookmark;
import com.ulticode.modules.bookmark.entity.BookmarkFolder;
import com.ulticode.modules.bookmark.entity.enums.BookmarkType;
import com.ulticode.modules.bookmark.mapper.BookmarkFolderMapper;
import com.ulticode.modules.bookmark.mapper.BookmarkMapper;
import com.ulticode.modules.bookmark.projection.FolderItemCount;
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
 * Implementation of BookmarkService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkServiceImpl implements BookmarkService {

    private final BookmarkFolderMapper folderMapper;
    private final BookmarkMapper bookmarkMapper;

    @Override
    @Transactional
    public QuickFavoriteVO quickFavorite(String userId, QuickFavoriteDTO dto) {
        BookmarkType targetType = dto.getTargetType();
        String targetId = dto.getTargetId();

        // Check if already favorited in any folder
        List<String> existingFolderIds = bookmarkMapper.findFolderIdsByTarget(userId, targetType.name(), targetId);

        if (!existingFolderIds.isEmpty()) {
            // Remove from all folders
            for (String folderId : existingFolderIds) {
                bookmarkMapper.deleteByFolderAndTarget(folderId, targetType.name(), targetId);
            }
            log.debug("Removed {}:{} from {} folders for user {}", targetType, targetId, existingFolderIds.size(), userId);
            return new QuickFavoriteVO(false, List.of());
        }

        // Get or create default folder
        BookmarkFolder defaultFolder = folderMapper.findDefaultByUserId(userId)
                .orElseGet(() -> createDefaultFolder(userId));

        // Add to default folder
        Bookmark bookmark = new Bookmark();
        bookmark.setFolderId(defaultFolder.getId());
        bookmark.setTargetId(targetId);
        bookmark.setTargetType(targetType.name());
        bookmark.setSortOrder(getNextSortOrder(defaultFolder.getId()));

        try {
            bookmarkMapper.insert(bookmark);
        } catch (DuplicateKeyException ex) {
            // 并发场景:在 findFolderIdsByTarget 与 insert 之间,另一线程已经插入了
            // 相同的 (collection_id, target_type, target_id),触发 collection_items 的
            // UNIQUE 索引。收敛到「当前真实状态」并返回成功,避免对用户暴露竞态错误。
            log.warn("Concurrent quickFavorite collision for user={}, type={}, id={}; converging to current state",
                    userId, targetType, targetId);
            List<String> currentFolderIds = bookmarkMapper.findFolderIdsByTarget(
                    userId, targetType.name(), targetId);
            return new QuickFavoriteVO(!currentFolderIds.isEmpty(), currentFolderIds);
        }

        log.debug("Quick favorited {}:{} for user {} in folder {}", targetType, targetId, userId, defaultFolder.getId());
        return new QuickFavoriteVO(true, List.of(defaultFolder.getId()));
    }

    @Override
    public List<BookmarkFolderVO> getFolders(String userId) {
        List<BookmarkFolder> folders = folderMapper.findByUserId(userId);
        Map<String, Long> itemCounts = itemCountsByFolderIds(folders);
        return folders.stream()
                .map(folder -> toFolderVO(folder, itemCounts.getOrDefault(folder.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    public BookmarkFolderDetailVO getFolderDetail(String userId, String folderId) {
        BookmarkFolder folder = getFolderAndValidateOwnership(folderId, userId);

        List<Bookmark> bookmarks = bookmarkMapper.findByFolderId(folderId);
        List<BookmarkVO> bookmarkVOs = bookmarks.stream()
                .map(this::toBookmarkVO)
                .collect(Collectors.toList());

        BookmarkFolderDetailVO vo = new BookmarkFolderDetailVO();
        vo.setId(folder.getId());
        vo.setName(folder.getName());
        vo.setDescription(folder.getDescription());
        vo.setIcon(folder.getIcon());
        vo.setColor(folder.getColor());
        vo.setSortOrder(folder.getSortOrder());
        vo.setIsDefault(folder.getIsDefault());
        vo.setItems(bookmarkVOs);
        vo.setCreatedAt(folder.getCreatedAt());
        vo.setUpdatedAt(folder.getUpdatedAt());
        return vo;
    }

    @Override
    @Transactional
    public BookmarkFolderVO createFolder(String userId, CreateFolderDTO dto) {
        // Check if folder name already exists
        Optional<BookmarkFolder> existing = folderMapper.findByUserIdAndName(userId, dto.getName());
        if (existing.isPresent()) {
            throw new BusinessException(ErrorCode.BOOKMARK_FOLDER_NAME_EXISTS);
        }

        // Ensure user has a default folder
        folderMapper.findDefaultByUserId(userId)
                .orElseGet(() -> createDefaultFolder(userId));

        BookmarkFolder folder = new BookmarkFolder();
        folder.setUserId(userId);
        folder.setName(dto.getName());
        folder.setDescription(dto.getDescription());
        folder.setIcon(dto.getIcon());
        folder.setColor(dto.getColor());
        folder.setSortOrder(getNextFolderSortOrder(userId));
        folder.setIsDefault(false);
        folderMapper.insert(folder);

        log.debug("Created folder {} for user {}", folder.getId(), userId);
        return toFolderVO(folder, 0);
    }

    @Override
    @Transactional
    public BookmarkFolderVO updateFolder(String userId, String folderId, UpdateFolderDTO dto) {
        BookmarkFolder folder = getFolderAndValidateOwnership(folderId, userId);

        // Check new name doesn't conflict
        if (dto.getName() != null && !dto.getName().equals(folder.getName())) {
            Optional<BookmarkFolder> existing = folderMapper.findByUserIdAndName(userId, dto.getName());
            if (existing.isPresent()) {
                throw new BusinessException(ErrorCode.BOOKMARK_FOLDER_NAME_EXISTS);
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
        return toFolderVO(folder, bookmarkMapper.countByFolderId(folderId));
    }

    @Override
    @Transactional
    public void deleteFolder(String userId, String folderId) {
        BookmarkFolder folder = getFolderAndValidateOwnership(folderId, userId);

        if (Boolean.TRUE.equals(folder.getIsDefault())) {
            throw new BusinessException(ErrorCode.BOOKMARK_CANNOT_DELETE_DEFAULT);
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
        // Validate folder ownership
        getFolderAndValidateOwnership(folderId, userId);

        // Check if already in this folder
        Optional<Bookmark> existing = bookmarkMapper.findByFolderAndTarget(
                folderId, dto.getTargetType().name(), dto.getTargetId());
        if (existing.isPresent()) {
            return toBookmarkVO(existing.get());
        }

        Bookmark bookmark = new Bookmark();
        bookmark.setFolderId(folderId);
        bookmark.setTargetId(dto.getTargetId());
        bookmark.setTargetType(dto.getTargetType().name());
        bookmark.setNote(dto.getNote());
        bookmark.setSortOrder(getNextSortOrder(folderId));
        bookmarkMapper.insert(bookmark);

        log.debug("Added bookmark {}:{} to folder {} for user {}",
                dto.getTargetType(), dto.getTargetId(), folderId, userId);
        return toBookmarkVO(bookmark);
    }

    @Override
    @Transactional
    public void removeBookmark(String userId, String folderId, String bookmarkId) {
        // Validate folder ownership
        getFolderAndValidateOwnership(folderId, userId);

        Bookmark bookmark = bookmarkMapper.selectById(bookmarkId);
        if (bookmark == null || !bookmark.getFolderId().equals(folderId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Bookmark not found in this folder");
        }

        bookmarkMapper.deleteById(bookmarkId);
        log.debug("Removed bookmark {} from folder {} for user {}", bookmarkId, folderId, userId);
    }

    @Override
    @Transactional
    public void removeBookmarkByTarget(String userId, String folderId, BookmarkType targetType, String targetId) {
        // Validate folder ownership
        getFolderAndValidateOwnership(folderId, userId);

        int deleted = bookmarkMapper.deleteByFolderAndTarget(folderId, targetType.name(), targetId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Bookmark not found in this folder");
        }
        log.debug("Removed {}:{} from folder {} for user {}", targetType, targetId, folderId, userId);
    }

    @Override
    @Transactional
    public BookmarkVO updateBookmark(String userId, String folderId, String bookmarkId, UpdateBookmarkDTO dto) {
        // Validate folder ownership
        getFolderAndValidateOwnership(folderId, userId);

        Bookmark bookmark = bookmarkMapper.selectById(bookmarkId);
        if (bookmark == null || !bookmark.getFolderId().equals(folderId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Bookmark not found in this folder");
        }

        if (dto.getNote() != null) {
            bookmark.setNote(dto.getNote());
        }
        if (dto.getSortOrder() != null) {
            bookmark.setSortOrder(dto.getSortOrder());
        }

        bookmarkMapper.updateById(bookmark);
        log.debug("Updated bookmark {} in folder {} for user {}", bookmarkId, folderId, userId);
        return toBookmarkVO(bookmark);
    }

    @Override
    public ItemFoldersVO getItemFolders(String userId, BookmarkType targetType, String targetId) {
        List<BookmarkFolder> folders = folderMapper.findByUserAndTarget(userId, targetType.name(), targetId);

        Map<String, Long> itemCounts = itemCountsByFolderIds(folders);
        List<BookmarkFolderVO> folderVOs = folders.stream()
                .map(folder -> toFolderVO(folder, itemCounts.getOrDefault(folder.getId(), 0L)))
                .collect(Collectors.toList());

        ItemFoldersVO vo = new ItemFoldersVO();
        vo.setTargetId(targetId);
        vo.setTargetType(targetType.name());
        vo.setIsFavorited(!folders.isEmpty());
        vo.setFolders(folderVOs);
        return vo;
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
                throw new BusinessException(ErrorCode.BOOKMARK_FOLDER_NOT_FOUND, "Folder not found: " + folderId);
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

    // ==================== Private Helper Methods ====================

    private BookmarkFolder getFolderAndValidateOwnership(String folderId, String userId) {
        BookmarkFolder folder = folderMapper.selectById(folderId);
        if (folder == null) {
            throw new BusinessException(ErrorCode.BOOKMARK_FOLDER_NOT_FOUND);
        }
        if (!folder.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot access other user's folder");
        }
        return folder;
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

    private Integer getNextFolderSortOrder(String userId) {
        Integer max = folderMapper.getMaxSortOrder(userId);
        return max == null ? 1 : max + 1;
    }

    private Integer getNextSortOrder(String folderId) {
        Integer max = bookmarkMapper.getMaxSortOrder(folderId);
        return max == null ? 0 : max + 1;
    }

    /**
     * Resolve item counts for the given folders in a single query, returning a
     * folder-id to count map. Folders holding no items are absent from the map;
     * callers resolve them to {@code 0L} via {@link Map#getOrDefault}.
     *
     * @param folders the folders to project
     * @return folder-id to item-count map (empty if no folders)
     */
    private Map<String, Long> itemCountsByFolderIds(List<BookmarkFolder> folders) {
        if (folders.isEmpty()) {
            return Map.of();
        }
        List<String> folderIds = folders.stream()
                .map(BookmarkFolder::getId)
                .collect(Collectors.toList());
        return bookmarkMapper.countItemsByFolderIds(folderIds).stream()
                .collect(Collectors.toMap(FolderItemCount::folderId, FolderItemCount::itemCount));
    }

    private BookmarkFolderVO toFolderVO(BookmarkFolder folder, long itemCount) {
        BookmarkFolderVO vo = new BookmarkFolderVO();
        vo.setId(folder.getId());
        vo.setName(folder.getName());
        vo.setDescription(folder.getDescription());
        vo.setIcon(folder.getIcon());
        vo.setColor(folder.getColor());
        vo.setSortOrder(folder.getSortOrder());
        vo.setIsDefault(folder.getIsDefault());
        vo.setItemCount((int) itemCount);
        vo.setCreatedAt(folder.getCreatedAt());
        vo.setUpdatedAt(folder.getUpdatedAt());
        return vo;
    }

    private BookmarkVO toBookmarkVO(Bookmark bookmark) {
        BookmarkVO vo = new BookmarkVO();
        vo.setId(bookmark.getId());
        vo.setFolderId(bookmark.getFolderId());
        vo.setTargetId(bookmark.getTargetId());
        vo.setTargetType(bookmark.getTargetType());
        vo.setSortOrder(bookmark.getSortOrder());
        vo.setNote(bookmark.getNote());
        vo.setCreatedAt(bookmark.getCreatedAt());
        return vo;
    }
}
