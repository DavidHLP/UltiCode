package com.ulticode.app.user.port;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * Transitional Q-write mapper for the ban columns on the Auth-owned
 * {@code users} table (P7-RELOCATE).
 *
 * <p>Seam for {@code ModerationAccountPort.updateBanStatus}: the
 * moderation state machine (relocated to backend-app) flips
 * {@code is_banned}/{@code banned_reason} in the same local transaction
 * as the {@code user_bans} sink insert, which the Dubbo
 * {@code AccountAdministrationService.changeState} flow cannot provide
 * (it is a separate remote transaction gated on an optimistic-lock
 * expected version). Mirrors the legacy adapter semantics exactly:
 * a direct column update on {@code users}.
 *
 * <p>This is a transitional seam: once the Auth ownership cutover for
 * ban state completes, this mapper and its caller are deleted in favor
 * of the RPC write path.
 */
@Mapper
public interface UserBanWriteMapper {

    @Update("UPDATE users SET is_banned = #{isBanned}, banned_reason = #{bannedReason} "
            + "WHERE id = #{userId} AND is_deleted = 0")
    int updateBanStatus(@Param("userId") String userId,
                        @Param("isBanned") boolean isBanned,
                        @Param("bannedReason") String bannedReason);
}
