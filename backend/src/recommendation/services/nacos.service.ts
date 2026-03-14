import { Injectable, Logger, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NacosServiceInstance, RecommendationConfig } from '../interfaces/recommendation.interface';

/**
 * Nacos naming client for service discovery
 * Provides integration with Nacos for discovering recommendation service instances
 */
@Injectable()
export class NacosNamingService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(NacosNamingService.name);
  private readonly config: RecommendationConfig;
  private nacosClient: any = null;
  private isInitialized = false;

  constructor(private readonly configService: ConfigService) {
    this.config = {
      enabled: this.configService.get<string>('RECOMMENDATION_ENABLED', 'true') === 'true',
      nacosServerAddr: this.configService.get<string>(
        'NACOS_SERVER_ADDR',
        'localhost:28848',
      ),
      nacosNamespace: this.configService.get<string>('NACOS_NAMESPACE', 'public'),
      nacosGroup: this.configService.get<string>('NACOS_GROUP', 'DEFAULT_GROUP'),
      serviceName: this.configService.get<string>(
        'RECOMMENDATION_SERVICE_NAME',
        'recommend-web',
      ),
      timeout: this.configService.get<number>('RECOMMENDATION_TIMEOUT', 5000),
      fallbackUrl: this.configService.get<string>('RECOMMENDATION_FALLBACK_URL'),
    };
  }

  async onModuleInit(): Promise<void> {
    if (!this.config.enabled) {
      this.logger.log('Recommendation service is disabled, skipping Nacos initialization');
      return;
    }

    try {
      await this.initializeNacosClient();
    } catch (error) {
      this.logger.warn(
        `Failed to initialize Nacos client: ${error instanceof Error ? error.message : String(error)}. Will use fallback URL if available.`,
      );
    }
  }

  async onModuleDestroy(): Promise<void> {
    if (this.nacosClient) {
      try {
        await this.nacosClient.close();
        this.logger.log('Nacos client closed');
      } catch (error) {
        this.logger.error(
          `Error closing Nacos client: ${error instanceof Error ? error.message : String(error)}`,
        );
      }
    }
  }

  /**
   * Initialize the Nacos naming client
   */
  private async initializeNacosClient(): Promise<void> {
    try {
      // Dynamic import to avoid errors when nacos package is not installed
      const { NacosNamingClient } = await import('nacos');

      this.nacosClient = new NacosNamingClient({
        serverList: this.config.nacosServerAddr,
        namespace: this.config.nacosNamespace === 'public' ? 'public' : this.config.nacosNamespace,
        logger: console,
      });

      await this.nacosClient.ready();
      this.isInitialized = true;
      this.logger.log(
        `Nacos client initialized successfully, connected to ${this.config.nacosServerAddr}`,
      );
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      this.logger.error(`Failed to initialize Nacos client: ${errorMessage}`);
      throw error;
    }
  }

  /**
   * Get a healthy service instance for the recommendation service
   * Uses round-robin load balancing
   */
  async getHealthyInstance(): Promise<NacosServiceInstance | null> {
    if (!this.config.enabled) {
      return null;
    }

    // If Nacos client is not available, return null (will use fallback URL)
    if (!this.isInitialized || !this.nacosClient) {
      this.logger.debug('Nacos client not initialized, returning null');
      return null;
    }

    try {
      const instances = await this.nacosClient.getAllInstances(
        this.config.serviceName,
        this.config.nacosGroup,
      );

      if (!instances || instances.length === 0) {
        this.logger.warn(`No instances found for service: ${this.config.serviceName}`);
        return null;
      }

      // Filter healthy instances
      const healthyInstances = instances.filter(
        (instance: NacosServiceInstance) => instance.healthy && instance.enabled,
      );

      if (healthyInstances.length === 0) {
        this.logger.warn(`No healthy instances for service: ${this.config.serviceName}`);
        return null;
      }

      // Simple round-robin: pick a random healthy instance
      const randomIndex = Math.floor(Math.random() * healthyInstances.length);
      const selected = healthyInstances[randomIndex];

      this.logger.debug(
        `Selected instance: ${selected.ip}:${selected.port} (weight: ${selected.weight})`,
      );

      return selected;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      this.logger.error(`Error getting healthy instance: ${errorMessage}`);
      return null;
    }
  }

  /**
   * Get the service URL for the recommendation service
   * Falls back to configured fallback URL if Nacos discovery fails
   */
  async getServiceUrl(): Promise<string | null> {
    // If fallback URL is configured, prefer it for development
    if (this.config.fallbackUrl) {
      this.logger.debug(`Using fallback URL: ${this.config.fallbackUrl}`);
      return this.config.fallbackUrl;
    }

    const instance = await this.getHealthyInstance();
    if (!instance) {
      this.logger.warn('No healthy instance available and no fallback URL configured');
      return null;
    }

    return `http://${instance.ip}:${instance.port}`;
  }

  /**
   * Get the current configuration
   */
  getConfig(): RecommendationConfig {
    return { ...this.config };
  }

  /**
   * Check if the service is enabled
   */
  isEnabled(): boolean {
    return this.config.enabled;
  }

  /**
   * Check if Nacos client is initialized
   */
  isReady(): boolean {
    return this.isInitialized;
  }
}
