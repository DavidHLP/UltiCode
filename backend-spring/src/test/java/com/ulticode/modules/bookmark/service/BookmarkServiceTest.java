package com.ulticode.modules.bookmark.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.bookmark.dto.AddBookmarkDTO;
import com.ulticode.modules.bookmark.dto.QuickFavoriteDTO;
import com.ulticode.modules.bookmark.dto.UpdateFolderDTO;
import com.ulticode.modules.bookmark.entity.Bookmark;
import com.ulticode.modules.bookmark.entity.BookmarkFolder;
import com.ulticode.modules.bookmark.entity.enums.BookmarkType;
import com.ulticode.modules.bookmark.mapper.BookmarkFolderMapper;
import com.ulticode.modules.bookmark.mapper.BookmarkMapper;
import com.ulticode.modules.bookmark.service.impl.BookmarkServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BookmarkServiceImpl}.
 *
 * <p>Regression scenarios from {@code docs/bookmark-api-test-report-2026-06-11.md}
 * §T08 (quickFavorite second call BindingException) and §T10
 * (removeBookmarkByTarget always 500) are covered here.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookmarkService")
class BookmarkServiceTest {

    private static final String USER_ID = "user-001";
    private static final String OTHER_USER_ID = "user-002";
    private static final String FOLDER_ID = "folder-001";
    private static final String DEFAULT_FOLDER_ID = "default-folder";
    private static final String TARGET_ID = "problem-1";
    private static final String BOOKMARK_ID = "bookmark-001";

    @Mock
    private BookmarkFolderMapper folderMapper;

    @Mock
    private BookmarkMapper bookmarkMapper;

    private BookmarkServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BookmarkServiceImpl(folderMapper, bookmarkMapper);
    }

    @Nested
    @DisplayName("quickFavorite")
    class QuickFavorite {

        @Test
        @DisplayName("inserts into default folder when not favorited")
        void notFavorited_insertsIntoDefaultFolder() {
            // Arrange
            when(bookmarkMapper.findFolderIdsByTarget(USER_ID, "PROBLEM", TARGET_ID))
                    .thenReturn(List.of());
            BookmarkFolder defaultFolder = new BookmarkFolder();
            defaultFolder.setId(DEFAULT_FOLDER_ID);
            defaultFolder.setUserId(USER_ID);
            when(folderMapper.findDefaultByUserId(USER_ID)).thenReturn(Optional.of(defaultFolder));
            when(bookmarkMapper.getMaxSortOrder(DEFAULT_FOLDER_ID)).thenReturn(null);

            QuickFavoriteDTO dto = new QuickFavoriteDTO();
            dto.setTargetType(BookmarkType.PROBLEM);
            dto.setTargetId(TARGET_ID);

            // Act
            var vo = service.quickFavorite(USER_ID, dto);

            // Assert
            assertThat(vo.getIsFavorited()).isTrue();
            assertThat(vo.getFolderIds()).containsExactly(DEFAULT_FOLDER_ID);
            verify(bookmarkMapper).insert(any(Bookmark.class));
        }

        @Test
        @DisplayName("removes from all folders when already favorited (T08-call2 regression)")
        void alreadyFavorited_removesFromAllFolders() {
            // Arrange
            when(bookmarkMapper.findFolderIdsByTarget(USER_ID, "PROBLEM", TARGET_ID))
                    .thenReturn(List.of(FOLDER_ID));
            when(bookmarkMapper.deleteByFolderAndTarget(FOLDER_ID, "PROBLEM", TARGET_ID))
                    .thenReturn(1);

            QuickFavoriteDTO dto = new QuickFavoriteDTO();
            dto.setTargetType(BookmarkType.PROBLEM);
            dto.setTargetId(TARGET_ID);

            // Act
            var vo = service.quickFavorite(USER_ID, dto);

            // Assert
            assertThat(vo.getIsFavorited()).isFalse();
            assertThat(vo.getFolderIds()).isEmpty();
            verify(bookmarkMapper).deleteByFolderAndTarget(FOLDER_ID, "PROBLEM", TARGET_ID);
            verify(bookmarkMapper, never()).insert(any(Bookmark.class));
        }

        @Test
        @DisplayName("converges to current state on concurrent duplicate key collision (Task 4)")
        void concurrentInsert_convergesToCurrentState() {
            // Arrange — first check returns empty (not favorited); another thread
            // inserts the same row in between, second query returns the truth.
            when(bookmarkMapper.findFolderIdsByTarget(USER_ID, "PROBLEM", TARGET_ID))
                    .thenReturn(List.of())
                    .thenReturn(List.of(DEFAULT_FOLDER_ID));
            BookmarkFolder defaultFolder = new BookmarkFolder();
            defaultFolder.setId(DEFAULT_FOLDER_ID);
            when(folderMapper.findDefaultByUserId(USER_ID)).thenReturn(Optional.of(defaultFolder));
            when(bookmarkMapper.getMaxSortOrder(DEFAULT_FOLDER_ID)).thenReturn(null);
            org.mockito.Mockito.doThrow(new DuplicateKeyException("collision"))
                    .when(bookmarkMapper).insert(any(Bookmark.class));

            QuickFavoriteDTO dto = new QuickFavoriteDTO();
            dto.setTargetType(BookmarkType.PROBLEM);
            dto.setTargetId(TARGET_ID);

            // Act
            var vo = service.quickFavorite(USER_ID, dto);

            // Assert
            assertThat(vo.getIsFavorited()).isTrue();
            assertThat(vo.getFolderIds()).containsExactly(DEFAULT_FOLDER_ID);
        }
    }

    @Nested
    @DisplayName("removeBookmarkByTarget")
    class RemoveBookmarkByTarget {

        @Test
        @DisplayName("returns successfully when delete matches a row (T10 regression)")
        void existingTarget_succeeds() {
            // Arrange
            BookmarkFolder folder = new BookmarkFolder();
            folder.setId(FOLDER_ID);
            folder.setUserId(USER_ID);
            when(folderMapper.selectById(FOLDER_ID)).thenReturn(folder);
            when(bookmarkMapper.deleteByFolderAndTarget(FOLDER_ID, "PROBLEM", TARGET_ID))
                    .thenReturn(1);

            // Act + Assert — no exception
            service.removeBookmarkByTarget(USER_ID, FOLDER_ID, BookmarkType.PROBLEM, TARGET_ID);
            verify(bookmarkMapper).deleteByFolderAndTarget(FOLDER_ID, "PROBLEM", TARGET_ID);
        }

        @Test
        @DisplayName("throws NOT_FOUND when no rows deleted")
        void targetAbsent_throwsNotFound() {
            // Arrange
            BookmarkFolder folder = new BookmarkFolder();
            folder.setId(FOLDER_ID);
            folder.setUserId(USER_ID);
            when(folderMapper.selectById(FOLDER_ID)).thenReturn(folder);
            when(bookmarkMapper.deleteByFolderAndTarget(FOLDER_ID, "PROBLEM", TARGET_ID))
                    .thenReturn(0);

            // Act + Assert
            assertThatThrownBy(() -> service.removeBookmarkByTarget(
                    USER_ID, FOLDER_ID, BookmarkType.PROBLEM, TARGET_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("throws when folder belongs to other user")
        void otherUsersFolder_throwsForbidden() {
            // Arrange
            BookmarkFolder otherFolder = new BookmarkFolder();
            otherFolder.setId(FOLDER_ID);
            otherFolder.setUserId(OTHER_USER_ID);
            when(folderMapper.selectById(FOLDER_ID)).thenReturn(otherFolder);

            // Act + Assert
            assertThatThrownBy(() -> service.removeBookmarkByTarget(
                    USER_ID, FOLDER_ID, BookmarkType.PROBLEM, TARGET_ID))
                    .isInstanceOf(BusinessException.class);
            verify(bookmarkMapper, never())
                    .deleteByFolderAndTarget(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("addBookmark")
    class AddBookmark {

        @Test
        @DisplayName("returns existing bookmark when already in folder (idempotent)")
        void alreadyInFolder_returnsExisting() {
            // Arrange
            BookmarkFolder folder = new BookmarkFolder();
            folder.setId(FOLDER_ID);
            folder.setUserId(USER_ID);
            when(folderMapper.selectById(FOLDER_ID)).thenReturn(folder);
            Bookmark existing = new Bookmark();
            existing.setId(BOOKMARK_ID);
            existing.setFolderId(FOLDER_ID);
            existing.setTargetId(TARGET_ID);
            existing.setTargetType("PROBLEM");
            when(bookmarkMapper.findByFolderAndTarget(FOLDER_ID, "PROBLEM", TARGET_ID))
                    .thenReturn(Optional.of(existing));

            AddBookmarkDTO dto = new AddBookmarkDTO();
            dto.setTargetType(BookmarkType.PROBLEM);
            dto.setTargetId(TARGET_ID);

            // Act
            var vo = service.addBookmark(USER_ID, FOLDER_ID, dto);

            // Assert
            assertThat(vo.getId()).isEqualTo(BOOKMARK_ID);
            verify(bookmarkMapper, never()).insert(any(Bookmark.class));
        }
    }

    @Nested
    @DisplayName("updateFolder")
    class UpdateFolder {

        @Test
        @DisplayName("rejects rename when new name already taken by another folder")
        void nameConflict_throws() {
            // Arrange
            BookmarkFolder folder = new BookmarkFolder();
            folder.setId(FOLDER_ID);
            folder.setUserId(USER_ID);
            folder.setName("旧名");
            when(folderMapper.selectById(FOLDER_ID)).thenReturn(folder);
            BookmarkFolder conflict = new BookmarkFolder();
            conflict.setId("other-folder");
            conflict.setUserId(USER_ID);
            conflict.setName("新名");
            when(folderMapper.findByUserIdAndName(USER_ID, "新名"))
                    .thenReturn(Optional.of(conflict));

            UpdateFolderDTO dto = new UpdateFolderDTO();
            dto.setName("新名");

            // Act + Assert
            assertThatThrownBy(() -> service.updateFolder(USER_ID, FOLDER_ID, dto))
                    .isInstanceOf(BusinessException.class);
            verify(folderMapper, never()).updateById(any(BookmarkFolder.class));
        }

        @Test
        @DisplayName("accepts rename when only folder with same name is self")
        void renamesSelf_callsUpdateById() {
            // Arrange
            BookmarkFolder folder = new BookmarkFolder();
            folder.setId(FOLDER_ID);
            folder.setUserId(USER_ID);
            folder.setName("新名"); // user-provided name equals current → no conflict check
            when(folderMapper.selectById(FOLDER_ID)).thenReturn(folder);
            when(bookmarkMapper.countByFolderId(FOLDER_ID)).thenReturn(0L);

            UpdateFolderDTO dto = new UpdateFolderDTO();
            dto.setDescription("已修改");

            // Act
            var vo = service.updateFolder(USER_ID, FOLDER_ID, dto);

            // Assert
            assertThat(vo.getId()).isEqualTo(FOLDER_ID);
            verify(folderMapper).updateById(folder);
        }
    }
}