package com.ulticode.app.uuid;

import com.ulticode.common.uuid.UuidGenerator;
import org.springframework.stereotype.Component;

/**
 * Transitional adapter for app components that still consume the common UUID
 * port while their contracts are migrated to {@link AppUuidGenerator}.
 *
 * <p>The app service owns the production implementation; this adapter keeps
 * the remaining common-port consumers injectable without reintroducing a
 * dependency on backend-legacy.</p>
 */
@Component
public class CommonUuidGeneratorAdapter implements UuidGenerator {

    private final AppUuidGenerator delegate;

    public CommonUuidGeneratorAdapter(AppUuidGenerator delegate) {
        this.delegate = delegate;
    }

    @Override
    public String newId() {
        return delegate.newId();
    }
}
