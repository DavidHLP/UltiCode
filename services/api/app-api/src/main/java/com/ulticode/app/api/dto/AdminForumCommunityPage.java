package com.ulticode.app.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * ADMIN-007: paginated page of {@link AdminForumCommunityDTO}.
 *
 * @param rows  matching communities
 * @param total total match count (unbounded by pagination)
 */
public record AdminForumCommunityPage(List<AdminForumCommunityDTO> rows, long total) implements Serializable {
    private static final long serialVersionUID = 1L;

}
