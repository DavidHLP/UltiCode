package com.ulticode.modules.bookmark.projection;

import com.ulticode.modules.bookmark.dto.BookmarkFolderDetailVO;
import com.ulticode.modules.bookmark.dto.BookmarkFolderVO;
import com.ulticode.modules.bookmark.dto.BookmarkVO;
import com.ulticode.modules.bookmark.dto.ItemFoldersVO;
import com.ulticode.modules.bookmark.entity.Bookmark;
import com.ulticode.modules.bookmark.entity.BookmarkFolder;
import com.ulticode.modules.bookmark.entity.enums.BookmarkType;

import java.util.List;

/**
 * Deep module that owns entity-to-VO projection and read-side aggregation for
 * the bookmark collection domain.
 *
 * <p>Replaces the projection logic previously embedded in
 * {@code BookmarkServiceImpl}. The mutation module ({@code BookmarkService})
 * keeps the collection invariants — folder ownership, default-folder existence,
 * item ordering, duplicate-convergence — and delegates every read shape here so
 * the shaping rules (folder VO assembly, per-folder count aggregation, item VO
 * assembly, target-to-folders rollup) live in one place.
 *
 * <p>Why a separate module and not a private helper:
 * <ul>
 *   <li><b>Locality</b>: the count-aggregation query and the folder/item VO
 *       assemblies are read concerns; concentrating them here keeps the
 *       mutation module focused on state changes.</li>
 *   <li><b>Leverage</b>: {@code listFolders}, {@code itemFolders}, and the
 *       mutation-return shapers all share the same count map and VO builders.
 *       Sharing inside one module beats duplicating them across call sites.</li>
 *   <li><b>Interface is the test surface</b>: the read shapes are exercised
 *       here with mocked mappers; the mutation paths no longer mock those
 *       collaborators just to assert a shaped return.</li>
 * </ul>
 *
 * <p>Dependency category: <b>in-process</b> (only mapper reads). No adapter is
 * needed at the external seam.
 *
 * @author ulticode
 */
public interface BookmarkProjection {

    /**
     * List the user's folders in display order, each carrying its item count.
     *
     * @param userId the owning user
     * @return folder view objects in display order, never {@code null}
     */
    List<BookmarkFolderVO> listFolders(String userId);

    /**
     * Shape a single folder together with its items. The caller resolves and
     * owns the folder; this method fetches the items and assembles the detail
     * view.
     *
     * @param folder the owned folder to project
     * @return the folder detail view, never {@code null}
     */
    BookmarkFolderDetailVO folderDetail(BookmarkFolder folder);

    /**
     * Shape the folders that contain a given target, with the favorited flag
     * and per-folder item counts.
     *
     * @param userId     the owning user
     * @param targetType the target type
     * @param targetId   the target id
     * @return the target-to-folders rollup, never {@code null}
     */
    ItemFoldersVO itemFolders(String userId, BookmarkType targetType, String targetId);

    /**
     * Shape a folder with an explicit item count. Used by mutation paths to
     * return the just-written folder without re-reading aggregation.
     *
     * @param folder    the folder to shape
     * @param itemCount the item count to attach
     * @return the folder view object, never {@code null}
     */
    BookmarkFolderVO toFolderVO(BookmarkFolder folder, long itemCount);

    /**
     * Shape a single bookmark item.
     *
     * @param bookmark the item to shape
     * @return the bookmark view object, never {@code null}
     */
    BookmarkVO toBookmarkVO(Bookmark bookmark);
}
