import { Controller, Get } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse } from '@nestjs/swagger';
import { MonitoringService } from './monitoring.service';
import {
  SystemInfoDto,
  ResourceUsageDto,
  DatabaseStatsDto,
  QueueStatsDto,
  RedisStatsDto,
  SystemHealthDto,
} from './dto/system-info.dto';

@ApiTags('Admin - Monitoring')
@Controller('admin/monitoring')
export class MonitoringController {
  constructor(private readonly monitoringService: MonitoringService) {}

  @Get('system')
  @ApiOperation({ summary: 'Get system information' })
  @ApiResponse({
    status: 200,
    description: 'System information',
    type: SystemInfoDto,
  })
  async getSystemInfo(): Promise<SystemInfoDto> {
    return this.monitoringService.getSystemInfo();
  }

  @Get('resources')
  @ApiOperation({ summary: 'Get resource usage (CPU, memory, load)' })
  @ApiResponse({
    status: 200,
    description: 'Resource usage',
    type: ResourceUsageDto,
  })
  async getResourceUsage(): Promise<ResourceUsageDto> {
    return this.monitoringService.getResourceUsage();
  }

  @Get('database')
  @ApiOperation({ summary: 'Get database statistics' })
  @ApiResponse({
    status: 200,
    description: 'Database statistics',
    type: DatabaseStatsDto,
  })
  async getDatabaseStats(): Promise<DatabaseStatsDto> {
    return this.monitoringService.getDatabaseStats();
  }

  @Get('queues')
  @ApiOperation({ summary: 'Get queue statistics' })
  @ApiResponse({
    status: 200,
    description: 'Queue statistics',
    type: [QueueStatsDto],
  })
  async getQueueStats(): Promise<QueueStatsDto[]> {
    return this.monitoringService.getQueueStats();
  }

  @Get('redis')
  @ApiOperation({ summary: 'Get Redis statistics' })
  @ApiResponse({
    status: 200,
    description: 'Redis statistics',
    type: RedisStatsDto,
  })
  async getRedisStats(): Promise<RedisStatsDto> {
    return this.monitoringService.getRedisStats();
  }

  @Get('health')
  @ApiOperation({ summary: 'Get system health check' })
  @ApiResponse({
    status: 200,
    description: 'System health status',
    type: SystemHealthDto,
  })
  async getHealth(): Promise<SystemHealthDto> {
    return this.monitoringService.getHealthCheck();
  }
}
