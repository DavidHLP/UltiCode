// Module
export { RecommendationModule } from './recommendation.module';

// Controller
export { RecommendationController } from './recommendation.controller';

// Services
export { RecommendationService } from './services/recommendation.service';
export { NacosNamingService } from './services/nacos.service';

// DTOs
export type {
  GetRecommendationsDto,
  RecommendItemDto,
  RecommendResultDto,
  RecommendResponseDto,
} from './dto/recommend.dto';

// Interfaces
export type {
  RecommendScenario,
  RecommendRequest,
  RecommendItem,
  RecommendResult,
  RecommendResponse,
  NacosServiceInstance,
  RecommendationConfig,
} from './interfaces/recommendation.interface';

export type {
  RecommendationModuleOptions,
  RecommendationModuleAsyncOptions,
} from './interfaces/recommendation-module-options.interface';
