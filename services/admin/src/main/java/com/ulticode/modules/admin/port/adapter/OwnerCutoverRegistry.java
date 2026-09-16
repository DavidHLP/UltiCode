package com.ulticode.modules.admin.port.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single source of truth for Admin owner write cutover policy.
 *
 * <p>Flag-controlled seams expose their property key and default here. Domains
 * that are already remote-only remain explicit metadata with no configuration
 * switch, so a local fallback cannot be added accidentally.</p>
 */
@Configuration(proxyBeanMethods = false)
public class OwnerCutoverRegistry {

    static final String CONTEST_FLAG = "app.features.contest-dubbo-cutover";
    static final String MODERATION_FLAG = "app.features.moderation-dubbo-cutover";

    @Bean(name = "contestOwnerCutover")
    public OwnerCutoverGate contestOwnerCutover(
            @Value("${app.features.contest-dubbo-cutover:false}") boolean enabled) {
        return new OwnerCutoverGate(
                "contest", CONTEST_FLAG, false, enabled, OwnerCutoverGate.Policy.FAIL_CLOSED);
    }

    @Bean(name = "moderationOwnerCutover")
    public OwnerCutoverGate moderationOwnerCutover(
            @Value("${app.features.moderation-dubbo-cutover:false}") boolean enabled) {
        return new OwnerCutoverGate(
                "moderation", MODERATION_FLAG, false, enabled,
                OwnerCutoverGate.Policy.DELEGATE_LOCAL);
    }

    @Bean(name = "notificationOwnerCutover")
    public OwnerCutoverGate notificationOwnerCutover() {
        return new OwnerCutoverGate(
                "notification", null, true, true, OwnerCutoverGate.Policy.CUTOVER_REMOVED);
    }

    @Bean(name = "submissionOwnerCutover")
    public OwnerCutoverGate submissionOwnerCutover() {
        return new OwnerCutoverGate(
                "submission", null, true, true, OwnerCutoverGate.Policy.ALWAYS_REMOTE);
    }

    @Bean(name = "problemOwnerCutover")
    public OwnerCutoverGate problemOwnerCutover() {
        return new OwnerCutoverGate(
                "problem", null, true, true, OwnerCutoverGate.Policy.ALWAYS_REMOTE);
    }
}
