package com.ulticode.modules.subscription.projection;

import com.ulticode.modules.subscription.dto.SubscriptionDTO;
import com.ulticode.modules.subscription.entity.Subscription;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * Default (and only) adapter for {@link SubscriptionReadProjection}.
 *
 * <p>Behaviour is byte-for-byte identical to the
 * {@code SubscriptionServiceImpl#toDTO} method that previously inlined this
 * copy &mdash; the only change is locality: the entity&rarr;DTO shaping now
 * lives in one place behind the {@link SubscriptionReadProjection} seam
 * instead of inside the service. The service retains a {@code toDTO} method
 * that delegates here so existing callers (notably
 * {@code SubscriptionController}) keep working without churn.
 */
@Service
public class DefaultSubscriptionReadProjection implements SubscriptionReadProjection {

    @Override
    public SubscriptionDTO toDTO(Subscription entity) {
        if (entity == null) {
            return null;
        }

        SubscriptionDTO dto = new SubscriptionDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
