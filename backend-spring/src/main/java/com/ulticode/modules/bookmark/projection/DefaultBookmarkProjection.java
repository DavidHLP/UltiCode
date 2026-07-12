package com.ulticode.modules.bookmark.projection;

import com.ulticode.modules.bookmark.dto.BookmarkFolderDetailVO;
import com.ulticode.modules.bookmark.dto.BookmarkFolderVO;
import com.ulticode.modules.bookmark.dto.BookmarkVO;
import com.ulticode.modules.bookmark.dto.ItemFoldersVO;
import com.ulticode.modules.bookmark.entity.Bookmark;
import com.ulticode.modules.bookmark.entity.BookmarkFolder;
import com.ulticode.modules.bookmark.entity.enums.BookmarkType;
import com.ulticode.modules.bookmark.mapper.BookmarkFolderMapper;
import com.ulticode.modules.bookmark.mapper.BookmarkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link BookmarkProjection}. Owns every
 * entity-to-VO projection and read-side aggregation for the bookmark domain.
 *
 * <p>Logic moved verbatim from the former private helpers in
 * {@code BookmarkServiceImpl} ({@code itemCountsByFolderIds}, {@code toFolderVO},
 * {@code toBookmarkVO}) plus the three read bodies ({@code getFolders},
 * {@code getFolderDetail}, {@code getItemFolders}). The mutation module now
 * delegates its read shaping and mutation-return shaping here.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class DefaultBookmarkProjection implements BookmarkProjection {

    private final BookmarkFolderMapper folderMapper;
    private final BookmarkMapper bookmarkMapper;

    @Override
    public List<BookmarkFolderVO> listFolders(String userId) {
        List<BookmarkFolder> folders = folderMapper.findByUserId(userId);
        Map<String, Long> itemCounts = itemCountsByFolderIds(folders);
        return folders.stream()
                .map(folder -> toFolderVO(folder, itemCounts.getOrDefault(folder.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    public BookmarkFolderDetailVO folderDetail(BookmarkFolder folder) {
        List<Bookmark> bookmarks = bookmarkMapper.findByFolderId(folder.getId());
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
    public ItemFoldersVO itemFolders(String userId, BookmarkType targetType, String targetId) {
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
    public BookmarkFolderVO toFolderVO(BookmarkFolder folder, long itemCount) {
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

    @Override
    public BookmarkVO toBookmarkVO(Bookmark bookmark) {
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
}
