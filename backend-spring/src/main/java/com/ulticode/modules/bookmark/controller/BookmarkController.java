package com.ulticode.modules.bookmark.controller;

import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
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
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @Operation(summary = "Quick favorite/unfavorite an item")
    @PostMapping("/quick")
    public Result<QuickFavoriteVO> quickFavorite(@Valid @RequestBody QuickFavoriteDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(bookmarkService.quickFavorite(userId, dto));
    }

    @Operation(summary = "Get all folders for current user")
    @GetMapping("/folders")
    public Result<List<BookmarkFolderVO>> getFolders() {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(bookmarkService.getFolders(userId));
    }

    @Operation(summary = "Get folder with bookmarks")
    @GetMapping("/folders/{id}")
    public Result<BookmarkFolderDetailVO> getFolderDetail(@PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(bookmarkService.getFolderDetail(userId, id));
    }

    @Operation(summary = "Create a new folder")
    @PostMapping("/folders")
    public Result<BookmarkFolderVO> createFolder(@Valid @RequestBody CreateFolderDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(bookmarkService.createFolder(userId, dto));
    }

    @Operation(summary = "Update a folder")
    @PatchMapping("/folders/{id}")
    public Result<BookmarkFolderVO> updateFolder(
            @PathVariable String id,
            @Valid @RequestBody UpdateFolderDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(bookmarkService.updateFolder(userId, id, dto));
    }

    @Operation(summary = "Delete a folder")
    @DeleteMapping("/folders/{id}")
    public Result<Void> deleteFolder(@PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserId();
        bookmarkService.deleteFolder(userId, id);
        return Result.success();
    }

    @Operation(summary = "Add a bookmark to a folder")
    @PostMapping("/folders/{folderId}/items")
    public Result<BookmarkVO> addBookmark(
            @PathVariable String folderId,
            @Valid @RequestBody AddBookmarkDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(bookmarkService.addBookmark(userId, folderId, dto));
    }

    @Operation(summary = "Remove a bookmark from a folder")
    @DeleteMapping("/folders/{folderId}/items/{bookmarkId}")
    public Result<Void> removeBookmark(
            @PathVariable String folderId,
            @PathVariable String bookmarkId) {
        String userId = SecurityUtil.getCurrentUserId();
        bookmarkService.removeBookmark(userId, folderId, bookmarkId);
        return Result.success();
    }

    @Operation(summary = "Remove a bookmark by target")
    @DeleteMapping("/folders/{folderId}/items/target/{targetType}/{targetId}")
    public Result<Void> removeBookmarkByTarget(
            @PathVariable String folderId,
            @PathVariable BookmarkType targetType,
            @PathVariable String targetId) {
        String userId = SecurityUtil.getCurrentUserId();
        bookmarkService.removeBookmarkByTarget(userId, folderId, targetType, targetId);
        return Result.success();
    }

    @Operation(summary = "Update a bookmark")
    @PatchMapping("/folders/{folderId}/items/{bookmarkId}")
    public Result<BookmarkVO> updateBookmark(
            @PathVariable String folderId,
            @PathVariable String bookmarkId,
            @Valid @RequestBody UpdateBookmarkDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(bookmarkService.updateBookmark(userId, folderId, bookmarkId, dto));
    }

    @Operation(summary = "Get folders containing a specific item")
    @GetMapping("/item/{targetType}/{targetId}")
    public Result<ItemFoldersVO> getItemFolders(
            @PathVariable BookmarkType targetType,
            @PathVariable String targetId) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(bookmarkService.getItemFolders(userId, targetType, targetId));
    }

    @Operation(summary = "Reorder folders")
    @PostMapping("/folders/reorder")
    public Result<Void> reorderFolders(@Valid @RequestBody ReorderFoldersDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        bookmarkService.reorderFolders(userId, dto.getFolderIds());
        return Result.success();
    }
}
