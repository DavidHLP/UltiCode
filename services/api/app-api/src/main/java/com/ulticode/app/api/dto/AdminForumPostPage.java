package com.ulticode.app.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * ADMIN-007: paginated page of {@link AdminForumPostRowDTO}.
 *
 * @param rows  matching rows
 * @param total total match count (unbounded by pagination)
 */
public record AdminForumPostPage(List<AdminForumPostRowDTO> rows, long total) implements Serializable {
}
