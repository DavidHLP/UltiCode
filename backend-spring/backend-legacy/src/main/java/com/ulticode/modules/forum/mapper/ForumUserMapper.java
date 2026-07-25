package com.ulticode.modules.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.forum.entity.ForumUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * ForumUser mapper for forum_users table.
 */
@Mapper
public interface ForumUserMapper extends BaseMapper<ForumUser> {
}
