package com.ulticode.app.bookmark.provider;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.app.api.service.BookmarkReadPort;
import com.ulticode.modules.bookmark.entity.Bookmark;
import com.ulticode.modules.bookmark.entity.enums.BookmarkType;
import com.ulticode.modules.bookmark.mapper.BookmarkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * App-side implementation of {@link BookmarkReadPort}.
 *
 * <p>Backs the legacy edge-operations read path after the bookmark family
 * relocated from backend-legacy to backend-app (P7-APP-BOOKMARK-001).
 * The caller consumes the port interface from {@code backend-app-api}
 * and never touches {@link BookmarkMapper} or {@link Bookmark} directly.
 *
 * <p>Internalises the {@link BookmarkType#leafTypeNames()} gate that the
 * caller previously inlined: non-leaf target types return {@code 0}
 * without a database round-trip.
 */
@Component
@RequiredArgsConstructor
public class BookmarkReadAdapter implements BookmarkReadPort {

    private final BookmarkMapper bookmarkMapper;

    @Override
    public long countFavoritesByTarget(String targetType, String targetId) {
        if (!BookmarkType.leafTypeNames().contains(targetType)) {
            return 0L;
        }
        QueryWrapper<Bookmark> wrapper = new QueryWrapper<>();
        wrapper.eq("target_id", targetId)
                .eq("target_type", targetType);
        return bookmarkMapper.selectCount(wrapper);
    }
}
