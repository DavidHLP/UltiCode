package com.ulticode.app.userprofile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.app.userprofile.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis-Plus mapper for {@link UserProfile} (App-owned profile table).
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {

    /**
     * Serializes avatar replacement: two concurrent uploads that read the same
     * previous key would both queue only that key and leave the object written
     * by the losing transaction orphaned. A locking read makes the second
     * transaction observe the key the first one actually displaced.
     */
    @Select("SELECT * FROM user_profiles WHERE account_id = #{accountId} FOR UPDATE")
    UserProfile selectByIdForUpdate(@Param("accountId") String accountId);
}
