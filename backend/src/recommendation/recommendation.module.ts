import { Module, DynamicModule, Global } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { RecommendationController } from './recommendation.controller';
import { RecommendationService } from './services/recommendation.service';
import { NacosNamingService } from './services/nacos.service';
import {
  RecommendationModuleAsyncOptions,
  RecommendationModuleOptions,
} from './interfaces/recommendation-module-options.interface';

@Global()
@Module({})
export class RecommendationModule {
  /**
   * Register the recommendation module with synchronous options
   */
  static register(options: RecommendationModuleOptions): DynamicModule {
    return {
      module: RecommendationModule,
      imports: [HttpModule, ConfigModule],
      controllers: [RecommendationController],
      providers: [
        {
          provide: 'RECOMMENDATION_OPTIONS',
          useValue: options,
        },
        NacosNamingService,
        RecommendationService,
      ],
      exports: [RecommendationService, NacosNamingService],
      global: options.isGlobal ?? true,
    };
  }

  /**
   * Register the recommendation module with asynchronous options
   */
  static registerAsync(
    options: RecommendationModuleAsyncOptions,
  ): DynamicModule {
    return {
      module: RecommendationModule,
      imports: [HttpModule, ConfigModule, ...(options.imports || [])],
      controllers: [RecommendationController],
      providers: [
        {
          provide: 'RECOMMENDATION_OPTIONS',
          useFactory: options.useFactory,
          inject: options.inject || [],
        },
        NacosNamingService,
        RecommendationService,
      ],
      exports: [RecommendationService, NacosNamingService],
      global: true,
    };
  }

  /**
   * Register the recommendation module using environment variables
   */
  static forRoot(): DynamicModule {
    return {
      module: RecommendationModule,
      imports: [
        HttpModule.registerAsync({
          imports: [ConfigModule],
          inject: [ConfigService],
          useFactory: (configService: ConfigService) => ({
            timeout: configService.get<number>('RECOMMENDATION_TIMEOUT', 5000),
            maxRedirects: 3,
          }),
        }),
        ConfigModule,
      ],
      controllers: [RecommendationController],
      providers: [NacosNamingService, RecommendationService],
      exports: [RecommendationService, NacosNamingService],
      global: true,
    };
  }
}
