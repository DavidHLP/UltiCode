package com.ulticode.common.system;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

/**
 * Production adapter for {@link SystemProbe}.
 *
 * <p>Reads the three signals the monitoring inspector (and future JVM-aware
 * consumers) need: processor count, process CPU load, system CPU load.
 * All the {@code com.sun.management.OperatingSystemMXBean} cast and the
 * {@code Runtime.getRuntime().availableProcessors()} fallback now live here,
 * not in the consumers. The fallback chain matches the legacy behaviour of
 * {@code DefaultMonitoringInspector.getResourceUsage()} byte-for-byte:
 *
 * <ol>
 *   <li>Try {@code OperatingSystemMXBean.getAvailableProcessors()}.</li>
 *   <li>On any {@code UnsupportedOperationException} / container denial,
 *       fall back to {@code Runtime.getRuntime().availableProcessors()}.</li>
 * </ol>
 *
 * <p>The {@code com.sun.management} extension is not present on every JVM
 * (e.g. IBM J9, OpenJ9). When the cast fails the probe returns {@code -1.0}
 * so the caller can render an "unavailable" indicator.
 */
@Slf4j
@Component
public class JvmSystemProbe implements SystemProbe {

    @Override
    public int availableProcessors() {
        OperatingSystemMXBean osMXBean = null;
        try {
            osMXBean = ManagementFactory.getOperatingSystemMXBean();
        } catch (Exception e) {
            // broad catch: a container may deny the OS MXBean; fall through to the fallback.
            log.warn("Unable to retrieve OperatingSystemMXBean in container environment", e);
        }

        if (osMXBean != null) {
            try {
                return osMXBean.getAvailableProcessors();
            } catch (Exception e) {
                // fall through
            }
        }
        return Runtime.getRuntime().availableProcessors();
    }

    @Override
    public double processCpuLoad() {
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            return osBean.getProcessCpuLoad();
        } catch (Exception e) {
            // broad catch: load metric is best-effort; -1.0 tells the dashboard to omit it.
            return -1.0;
        }
    }

    @Override
    public double systemCpuLoad() {
        OperatingSystemMXBean osMXBean;
        try {
            osMXBean = ManagementFactory.getOperatingSystemMXBean();
        } catch (Exception e) {
            return -1.0;
        }
        if (osMXBean == null) {
            return -1.0;
        }
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                    (com.sun.management.OperatingSystemMXBean) osMXBean;
            return osBean.getCpuLoad();
        } catch (Exception e) {
            // Fallback to system load average
            double loadAverage = osMXBean.getSystemLoadAverage();
            int processors;
            try {
                processors = osMXBean.getAvailableProcessors();
            } catch (Exception ex) {
                processors = Runtime.getRuntime().availableProcessors();
            }
            if (loadAverage >= 0 && processors > 0) {
                return loadAverage / processors;
            }
            return -1.0;
        }
    }
}
