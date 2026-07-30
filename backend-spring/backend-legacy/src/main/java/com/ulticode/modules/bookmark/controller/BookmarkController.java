package com.ulticode.modules.bookmark.controller;

import com.ulticode.websecurity.annotation.RateLimit;
import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.bookmark.dto.*;
import com.ulticode.modules.bookmark.entity.enums.BookmarkType;
import com.ulticode.modules.bookmark.service.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for bookmark operations.
 */
@Tag(name = "Bookmark", description = "Bookmark management API")
@RestController
@RequestMapping("/bookmarks")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class BookmarkController {

    private final BookmarkService bookmarkService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "Quick favorite/unfavorite an item")
    @RateLimit(key = "bookmark:quick", limit = 20, period = 60)
    @PostMapping("/quick")
    public Result<QuickFavoriteVO> quickFavorite(@Valid @RequestBody QuickFavoriteDTO dto) {
        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(bookmarkService.quickFavorite(userId, dto));
    }

    @Operation(summary = "Get all folders for current user")
    @GetMapping("/folders")
    public Result<List<BookmarkFolderVO>> getFolders() {
        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(bookmarkService.getFolders(userId));
    }

    @Operation(summary = "Get folder with bookmarks")
    @GetMapping("/folders/{id}")
    public Result<BookmarkFolderDetailVO> getFolderDetail(@PathVariable String id) {
        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(bookmarkService.getFolderDetail(userId, id));
    }

    @Operation(summary = "Create a new folder")
    @RateLimit(key = "bookmark:create-folder", limit = 20, period = 60)
    @PostMapping("/folders")
    public Result<BookmarkFolderVO> createFolder(@Valid @RequestBody CreateFolderDTO dto) {
        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(bookmarkService.createFolder(userId, dto));
    }

    @Operation(summary = "Update a folder")
    @RateLimit(key = "bookmark:update-folder", limit = 20, period = 60)
    @PatchMapping("/folders/{id}")
    public Result<BookmarkFolderVO> updateFolder(
            @PathVariable String id,
            @Valid @RequestBody UpdateFolderDTO dto) {
        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(bookmarkService.updateFolder(userId, id, dto));
    }

    @Operation(summary = "Delete a folder")
    @RateLimit(key = "bookmark:delete-folder", limit = 20, period = 60)
    @DeleteMapping("/folders/{id}")
    public Result<Void> deleteFolder(@PathVariable String id) {
        String userId = currentUserProvider.getCurrentUserId();
        bookmarkService.deleteFolder(userId, id);
        return Result.success();
    }

    @Operation(summary = "Add a bookmark to a folder")
    @RateLimit(key = "bookmark:add", limit = 20, period = 60)
    @PostMapping("/folders/{folderId}/items")
    public Result<BookmarkVO> addBookmark(
            @PathVariable String folderId,
            @Valid @RequestBody AddBookmarkDTO dto) {
        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(bookmarkService.addBookmark(userId, folderId, dto));
    }

    @Operation(summary = "Remove a bookmark from a folder")
    @RateLimit(key = "bookmark:remove", limit = 20, period = 60)
    @DeleteMapping("/folders/{folderId}/items/{bookmarkId}")
    public Result<Void> removeBookmark(
            @PathVariable String folderId,
            @PathVariable String bookmarkId) {
        String userId = currentUserProvider.getCurrentUserId();
        bookmarkService.removeBookmark(userId, folderId, bookmarkId);
        return Result.success();
    }

    @Operation(summary = "Remove a bookmark by target")
    @RateLimit(key = "bookmark:remove-by-target", limit = 20, period = 60)
    @DeleteMapping("/folders/{folderId}/items/target/{targetType}/{targetId}")
    public Result<Void> removeBookmarkByTarget(
            @PathVariable String folderId,
            @PathVariable BookmarkType targetType,
            @PathVariable String targetId) {
        String userId = currentUserProvider.getCurrentUserId();
        bookmarkService.removeBookmarkByTarget(userId, folderId, targetType, targetId);
        return Result.success();
    }

    @Operation(summary = "Update a bookmark")
    @RateLimit(key = "bookmark:update", limit = 20, period = 60)
    @PatchMapping("/folders/{folderId}/items/{bookmarkId}")
    public Result<BookmarkVO> updateBookmark(
            @PathVariable String folderId,
            @PathVariable String bookmarkId,
            @Valid @RequestBody UpdateBookmarkDTO dto) {
        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(bookmarkService.updateBookmark(userId, folderId, bookmarkId, dto));
    }

    @Operation(summary = "Get folders containing a specific item")
    @GetMapping("/item/{targetType}/{targetId}")
    public Result<ItemFoldersVO> getItemFolders(
            @PathVariable BookmarkType targetType,
            @PathVariable String targetId) {
        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(bookmarkService.getItemFolders(userId, targetType, targetId));
    }

    @Operation(summary = "Reorder folders")
    @RateLimit(key = "bookmark:reorder", limit = 20, period = 60)
    @PostMapping("/folders/reorder")
    public Result<Void> reorderFolders(@Valid @RequestBody ReorderFoldersDTO dto) {
        String userId = currentUserProvider.getCurrentUserId();
        bookmarkService.reorderFolders(userId, dto.getFolderIds());
        return Result.success();
    }
}
