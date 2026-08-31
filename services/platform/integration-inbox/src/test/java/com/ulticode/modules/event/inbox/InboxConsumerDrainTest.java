package com.ulticode.modules.event.inbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

@DisplayName("InboxConsumer graceful drain")
class InboxConsumerDrainTest {

    @Test
    @DisplayName("draining keeps new inbox claims out of the database")
    void drainStopsNewClaims() {
        AtomicBoolean claimed = new AtomicBoolean();
        ConsumerInboxMapper inboxMapper = (ConsumerInboxMapper) Proxy.newProxyInstance(
                ConsumerInboxMapper.class.getClassLoader(),
                new Class<?>[]{ConsumerInboxMapper.class},
                (proxy, method, args) -> {
                    claimed.set(true);
                    return method.getReturnType().isPrimitive()
                            ? defaultValue(method.getReturnType()) : null;
                });
        InboxConsumer consumer = new InboxConsumer(inboxMapper, "Graceful", null);
        consumer.beginDrain();

        assertThat(consumer.consume()).isZero();
        assertThat(claimed).isFalse();
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) return false;
        if (returnType == byte.class) return (byte) 0;
        if (returnType == short.class) return (short) 0;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == float.class) return 0F;
        if (returnType == double.class) return 0D;
        if (returnType == char.class) return '\0';
        return null;
    }
}
