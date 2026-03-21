package com.ulticode.modules.monitoring.service;

import com.ulticode.modules.monitoring.dto.DatabaseStatsVO;
import com.ulticode.modules.monitoring.dto.QueueStatsVO;
import com.ulticode.modules.monitoring.dto.RedisStatsVO;
import com.ulticode.modules.monitoring.dto.ResourceUsageVO;
import com.ulticode.modules.monitoring.dto.SystemHealthVO;
import com.ulticode.modules.monitoring.dto.SystemInfoVO;

import java.util.List;

/**
 * Service interface for system monitoring operations.
 */
public interface MonitoringService {

    /**
     * Get system information.
     *
     * @return system info VO
     */
    SystemInfoVO getSystemInfo();

    /**
     * Get current resource usage.
     *
     * @return resource usage VO
     */
    ResourceUsageVO getResourceUsage();

    /**
     * Get database statistics.
     *
     * @return database stats VO
     */
    DatabaseStatsVO getDatabaseStats();

    /**
     * Get queue statistics.
     *
     * @return list of queue stats VOs
     */
    List<QueueStatsVO> getQueueStats();

    /**
     * Get Redis statistics.
     *
     * @return redis stats VO
     */
    RedisStatsVO getRedisStats();

    /**
     * Perform system health check.
     *
     * @return system health VO
     */
    SystemHealthVO getHealthCheck();
}
