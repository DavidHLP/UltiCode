package com.ulticode.core;

import com.ulticode.common.lifecycle.DrainGate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

/** Owns the Core process drain gate; child Owner contexts close behind it. */
@Configuration(proxyBeanMethods = false)
public class CoreLifecycleConfiguration {

    @Bean
    DrainGate coreDrainGate() {
        return new DrainGate();
    }

    @Bean
    CoreDrainListener coreDrainListener(DrainGate drainGate) {
        return new CoreDrainListener(drainGate);
    }

    static final class CoreDrainListener {
        private final DrainGate drainGate;

        CoreDrainListener(DrainGate drainGate) {
            this.drainGate = drainGate;
        }

        @EventListener
        public void onContextClosed(ContextClosedEvent ignored) {
            drainGate.beginDrain();
        }
    }
}
