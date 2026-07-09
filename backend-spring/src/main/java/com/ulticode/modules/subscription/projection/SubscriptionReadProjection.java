package com.ulticode.modules.subscription.projection;

import com.ulticode.modules.subscription.dto.SubscriptionDTO;
import com.ulticode.modules.subscription.entity.Subscription;

/**
 * Read-side deep module owning every entity&rarr;DTO projection rule for the
 * subscription domain.
 *
 * <p>Mirrors the projection deep modules already established in this codebase
 * &mdash; {@code ModerationProjection}, {@code AchievementProjection},
 * {@code AdminUserProjection} (see {@code backend/AGENTS.md} &sect; Deep
 * Modules). The service uses this seam to keep the read view shapes
 * in one place; controllers can also depend on it directly when they only
 * need a read view (see {@code SubscriptionController} future refactor).
 *
 * <p>Currently the only projection is the entity&rarr;{@code SubscriptionDTO}
 * copy. The seam exists so future read helpers (e.g. an expiry projection,
 * a summary projection for list views, a "soon-to-expire" lookup) land in
 * one file instead of as more private helpers in the service.
 */
public interface SubscriptionReadProjection {

    /**
     * Project a single {@link Subscription} entity into its DTO.
     *
     * <p>Same shape as the previous {@code SubscriptionServiceImpl#toDTO}
     * inlined copy &mdash; a {@link org.springframework.beans.BeanUtils}
     * copy that excludes the {@code startedAt}, {@code deletedAt} and
     * {@code isDeleted} columns not present on the DTO.
     *
     * @param entity the entity to project (may be {@code null})
     * @return the projected DTO, or {@code null} if the input is {@code null}
     */
    SubscriptionDTO toDTO(Subscription entity);
}
