package com.ulticode.app.userprofile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.app.userprofile.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for {@link UserProfile} (App-owned profile table).
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
