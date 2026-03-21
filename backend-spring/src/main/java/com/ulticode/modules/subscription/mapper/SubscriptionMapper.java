package com.ulticode.modules.subscription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.subscription.entity.Subscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/**
 * Mapper for Subscription entity.
 */
@Mapper
public interface SubscriptionMapper extends BaseMapper<Subscription> {

    /**
     * Find the most recent active subscription for a user.
     *
     * @param userId the user ID
     * @return the active subscription or null
     */
    @Select("SELECT * FROM subscriptions WHERE user_id = #{userId} AND status = 'ACTIVE' AND is_deleted = 0 ORDER BY created_at DESC LIMIT 1")
    Subscription findActiveByUserId(@Param("userId") String userId);

    /**
     * Check if user has an active subscription.
     *
     * @param userId the user ID
     * @return true if user has an active subscription
     */
    @Select("SELECT COUNT(*) > 0 FROM subscriptions WHERE user_id = #{userId} AND status = 'ACTIVE' AND is_deleted = 0")
    boolean existsActiveByUserId(@Param("userId") String userId);

    /**
     * Update subscription status by ID.
     *
     * @param id     the subscription ID
     * @param status the new status
     * @return number of rows updated
     */
    @Update("UPDATE subscriptions SET status = #{status}, updated_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * Cancel subscription by ID.
     *
     * @param id the subscription ID
     * @return number of rows updated
     */
    @Update("UPDATE subscriptions SET status = 'CANCELLED', cancelled_at = NOW(), updated_at = NOW() WHERE id = #{id}")
    int cancelById(@Param("id") String id);
}
