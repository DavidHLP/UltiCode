package com.ulticode.common.audit;

/**
 * Port the ban-check aspect uses to determine whether the current
 * principal is banned from posting content. Hides the user module's
 * {@code User} entity and {@code UserMapper} from the cross-cutting
 * {@code common/aspect/} package.
 *
 * <p>Prior to this port, {@code BanCheckAspect} imported
 * {@code com.ulticode.modules.user.entity.User} and
 * {@code com.ulticode.modules.user.mapper.UserMapper} directly. The
 * cross-cutting infrastructure knew the user's data shape. Renaming
 * the entity or moving the mapper would break the aspect.
 *
 * <p><strong>Seam justification:</strong>
 * <ul>
 *   <li>{@code UserBanCheckAdapter} (in {@code modules/user/port/adapter})
 *       reads the ban flag via the user module's own mapper.</li>
 *   <li>{@code StaticBanCheck} (in test sources) — fixed banned/unbanned
 *       answer per principal; no user-mock noise in aspect tests.</li>
 * </ul>
 *
 * <p>Mirrors the proven {@code RateLimiter} and {@code AuditSinkPort}
 * seams: aspect depends on the port, port's storage adapter lives next
 * to the data owner.
 *
 * @author ulticode
 */
public interface BanCheckPort {

    /**
     * @param userId the principal to check
     * @return {@code true} if the user is currently banned from posting
     *         content; {@code false} otherwise (including when the user
     *         does not exist — ban check is non-throwing by contract)
     */
    boolean isBanned(String userId);
}
