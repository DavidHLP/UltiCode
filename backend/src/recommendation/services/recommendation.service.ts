import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, timeout, retry, catchError } from 'rxjs';
import { AxiosError } from 'axios';
import {
  RecommendRequest,
  RecommendResponse,
  RecommendResult,
  RecommendScenario,
} from '../interfaces/recommendation.interface';
import { NacosNamingService } from './nacos.service';

/**
 * Service for fetching problem recommendations from the recommendation microservice
 * Uses Nacos for service discovery and HTTP for communication
 */
@Injectable()
export class RecommendationService {
  private readonly logger = new Logger(RecommendationService.name);
  private readonly defaultTimeout: number;

  constructor(
    private readonly configService: ConfigService,
    private readonly httpService: HttpService,
    private readonly nacosService: NacosNamingService,
  ) {
    this.defaultTimeout = this.configService.get<number>(
      'RECOMMENDATION_TIMEOUT',
      5000,
    );
  }

  /**
   * Get problem recommendations for a user
   *
   * @param request - The recommendation request
   * @returns The recommendation response
   */
  async getRecommendations(
    request: RecommendRequest,
  ): Promise<RecommendResponse<RecommendResult>> {
    if (!this.nacosService.isEnabled()) {
      return this.createDisabledResponse();
    }

    const serviceUrl = await this.nacosService.getServiceUrl();
    if (!serviceUrl) {
      return this.createErrorResponse(
        503,
        'Recommendation service unavailable',
      );
    }

    const url = `${serviceUrl}/api/recommend`;

    try {
      this.logger.debug(
        `Fetching recommendations for user ${request.userId}, scenario: ${request.scenario}`,
      );

      const response = await firstValueFrom(
        this.httpService
          .post<RecommendResponse<RecommendResult>>(url, request, {
            headers: {
              'Content-Type': 'application/json',
              Accept: 'application/json',
            },
          })
          .pipe(
            timeout(this.defaultTimeout),
            retry({
              count: 2,
              delay: 500,
            }),
            catchError((error: AxiosError) => {
              const errorMessage = error.response
                ? `HTTP ${error.response.status}: ${error.response.statusText}`
                : error.message;
              this.logger.error(
                `Recommendation service error: ${errorMessage}`,
              );
              throw error;
            }),
          ),
      );

      return response.data;
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : String(error);
      this.logger.error(`Failed to get recommendations: ${errorMessage}`);

      return this.createErrorResponse(
        500,
        `Failed to get recommendations: ${errorMessage}`,
      );
    }
  }

  /**
   * Get daily practice recommendations for a user
   */
  async getDailyRecommendations(
    userId: string,
    size = 10,
    includeSolved = false,
  ): Promise<RecommendResponse<RecommendResult>> {
    return this.getRecommendations({
      userId,
      size,
      scenario: RecommendScenario.DAILY,
      includeSolved,
    });
  }

  /**
   * Get similar problems to a given problem
   */
  async getSimilarProblems(
    userId: string,
    sourceProblemId: number,
    size = 5,
  ): Promise<RecommendResponse<RecommendResult>> {
    return this.getRecommendations({
      userId,
      size,
      scenario: RecommendScenario.SIMILAR,
      sourceProblemId,
    });
  }

  /**
   * Get weak point strengthening recommendations
   */
  async getWeakPointRecommendations(
    userId: string,
    size = 10,
    targetTags?: string[],
  ): Promise<RecommendResponse<RecommendResult>> {
    return this.getRecommendations({
      userId,
      size,
      scenario: RecommendScenario.WEAK_POINT,
      targetTags,
    });
  }

  /**
   * Get challenge mode recommendations (harder problems)
   */
  async getChallengeRecommendations(
    userId: string,
    size = 5,
  ): Promise<RecommendResponse<RecommendResult>> {
    return this.getRecommendations({
      userId,
      size,
      scenario: RecommendScenario.CHALLENGE,
    });
  }

  /**
   * Check if the recommendation service is healthy
   */
  async healthCheck(): Promise<{
    status: 'healthy' | 'unhealthy' | 'disabled';
    nacosReady: boolean;
    message: string;
  }> {
    if (!this.nacosService.isEnabled()) {
      return {
        status: 'disabled',
        nacosReady: false,
        message: 'Recommendation service is disabled',
      };
    }

    const nacosReady = this.nacosService.isReady();
    const serviceUrl = await this.nacosService.getServiceUrl();

    if (!serviceUrl) {
      return {
        status: 'unhealthy',
        nacosReady,
        message:
          'No service URL available (Nacos discovery failed or no fallback configured)',
      };
    }

    try {
      const response = await firstValueFrom(
        this.httpService
          .get(`${serviceUrl}/api/recommend/health`)
          .pipe(timeout(3000)),
      );

      return {
        status: 'healthy',
        nacosReady,
        message: `Connected to ${serviceUrl}`,
      };
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : String(error);
      return {
        status: 'unhealthy',
        nacosReady,
        message: `Failed to connect to ${serviceUrl}: ${errorMessage}`,
      };
    }
  }

  /**
   * Create an error response
   */
  private createErrorResponse(
    code: number,
    message: string,
  ): RecommendResponse<RecommendResult> {
    return {
      success: false,
      code,
      message,
      data: null,
    };
  }

  /**
   * Create a disabled response
   */
  private createDisabledResponse(): RecommendResponse<RecommendResult> {
    return {
      success: false,
      code: 503,
      message: 'Recommendation service is disabled',
      data: null,
    };
  }
}
