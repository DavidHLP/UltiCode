package com.ulticode.modules.forum.port;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.app.api.service.ForumTagReadPort;
import com.ulticode.app.api.service.ForumTagReadPort.ForumTagPage;
import com.ulticode.app.api.service.ForumTagReadPort.ForumTagRow;
import com.ulticode.modules.forum.entity.ForumTag;
import com.ulticode.modules.forum.mapper.ForumTagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * ADMIN-007: provider implementing {@link ForumTagReadPort} inside
 * {@code backend-app} (forum implementation module) so the Admin
 * service's {@code ForumTagHandler} lists / reads forum tags without
 * importing {@code ForumTag} or {@code ForumTagMapper}.
 *
 * @author ulticode
 */
@Component
@Primary
@RequiredArgsConstructor
public class DefaultForumTagReadAdapter implements ForumTagReadPort {

    private final ForumTagMapper forumTagMapper;

    @Override
    public ForumTagPage page(String search, int pageNum, int pageSize, String sortBy, String sortOrder) {
        LambdaQueryWrapper<ForumTag> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(search)) {
            wrapper.like(ForumTag::getName, search).or().like(ForumTag::getSlug, search);
        }
        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
        if ("usageCount".equalsIgnoreCase(sortBy) || "usage_count".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, isAsc, ForumTag::getUsageCount);
        } else {
            wrapper.orderBy(true, isAsc, ForumTag::getName);
        }

        Page<ForumTag> page = new Page<>(pageNum, pageSize);
        Page<ForumTag> result = forumTagMapper.selectPage(page, wrapper);

        List<ForumTagRow> rows = result.getRecords().stream().map(this::toRow).toList();
        return new ForumTagPage(rows, result.getTotal());
    }

    @Override
    public ForumTagRow getById(String id) {
        ForumTag tag = forumTagMapper.selectById(id);
        return tag != null ? toRow(tag) : null;
    }

    private ForumTagRow toRow(ForumTag tag) {
        return new ForumTagRow(
                tag.getId(),
                tag.getName(),
                tag.getSlug(),
                tag.getDescription(),
                tag.getColor(),
                tag.getUsageCount(),
                tag.getCreatedAt());
    }
}
