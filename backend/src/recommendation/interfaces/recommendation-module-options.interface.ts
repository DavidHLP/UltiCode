import { ModuleMetadata } from '@nestjs/common';
import { RecommendationConfig } from './recommendation.interface';

/**
 * Options for configuring the recommendation module
 */
export interface RecommendationModuleOptions
  extends Partial<RecommendationConfig> {
  /**
   * Whether the module should be globally available
   * @default true
   */
  isGlobal?: boolean;
}

/**
 * Asynchronous options for the recommendation module
 */
export interface RecommendationModuleAsyncOptions
  extends Pick<ModuleMetadata, 'imports'> {
  /**
   * Whether the module should be globally available
   * @default true
   */
  isGlobal?: boolean;

  /**
   * Factory function to create the module options
   */
  useFactory: (
    ...args: any[]
  ) => Promise<RecommendationModuleOptions> | RecommendationModuleOptions;

  /**
   * Dependencies to inject into the factory function
   */
  inject?: any[];
}
