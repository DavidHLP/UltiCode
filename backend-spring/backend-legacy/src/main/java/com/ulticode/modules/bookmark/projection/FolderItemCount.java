package com.ulticode.modules.bookmark.projection;

/**
 * Read projection pairing a bookmark folder with the number of items it holds.
 *
 * <p>Used to collapse per-folder count choreography out of the service: the
 * persistence seam returns item counts for a set of folders in a single query,
 * so {@code BookmarkService} expresses projection policy rather than an N+1
 * read loop.
 */
public record FolderItemCount(String folderId, Long itemCount) {
}
