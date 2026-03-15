import {
  Controller,
  Get,
  Post,
  Body,
  Query,
  Param,
  ParseIntPipe,
  UseGuards,
  HttpCode,
  HttpStatus,
  Req,
} from '@nestjs/common';
import { Throttle, ThrottlerGuard } from '@nestjs/throttler';
import {
  ApiTags,
  ApiOperation,
  ApiResponse,
  ApiBearerAuth,
  ApiQuery,
  ApiParam,
} from '@nestjs/swagger';
import { Request } from 'express';
import { RecommendationService } from './services/recommendation.service';
import {
  GetRecommendationsDto,
  RecommendResponseDto,
} from './dto/recommend.dto';
import { OptionalJwtAuthGuard } from '../auth/guards/optional-jwt-auth.guard';

interface RequestWithOptionalUser extends Request {
  user?: { id: string };
}

@ApiTags('recommendations')
@Controller('recommendations')
@UseGuards(ThrottlerGuard)
export class RecommendationController {
  constructor(private readonly recommendationService: RecommendationService) {}

  /**
   * Get personalized recommendations based on request parameters
   */
  @Post()
  @Throttle({ default: { limit: 30, ttl: 60000 } })
  @UseGuards(OptionalJwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Get personalized problem recommendations' })
  @ApiResponse({
    status: 200,
    description: 'Recommendations retrieved successfully',
    type: RecommendResponseDto,
  })
  @ApiResponse({
    status: 503,
    description: 'Recommendation service unavailable',
  })
  async getRecommendations(
    @Body() dto: GetRecommendationsDto,
    @Req() req: RequestWithOptionalUser,
  ): Promise<RecommendResponseDto> {
    // Use authenticated user ID if not provided in request
    const userId = dto.userId || req.user?.id;
    if (!userId) {
      return {
        success: false,
        code: 400,
        message: 'User ID is required',
        data: null,
      };
    }

    return this.recommendationService.getRecommendations({
      userId,
      size: dto.size,
      scenario: dto.scenario,
      sourceProblemId: dto.sourceProblemId,
      targetTags: dto.targetTags,
      includeSolved: dto.includeSolved,
    });
  }

  /**
   * Get daily practice recommendations
   */
  @Get('daily')
  @UseGuards(OptionalJwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Get daily practice recommendations' })
  @ApiQuery({
    name: 'size',
    required: false,
    type: Number,
    description: 'Number of recommendations (default: 10)',
  })
  @ApiQuery({
    name: 'includeSolved',
    required: false,
    type: Boolean,
    description: 'Include already solved problems (default: false)',
  })
  @ApiResponse({
    status: 200,
    description: 'Daily recommendations retrieved successfully',
    type: RecommendResponseDto,
  })
  async getDailyRecommendations(
    @Req() req: RequestWithOptionalUser,
    @Query('size') size?: number,
    @Query('includeSolved') includeSolved?: boolean,
  ): Promise<RecommendResponseDto> {
    const userId = req.user?.id;
    if (!userId) {
      return {
        success: false,
        code: 401,
        message: 'Authentication required',
        data: null,
      };
    }

    return this.recommendationService.getDailyRecommendations(
      userId,
      size ? Number(size) : 10,
      includeSolved === true,
    );
  }

  /**
   * Get similar problems to a given problem
   */
  @Get('similar/:problemId')
  @Throttle({ default: { limit: 10, ttl: 60000 } })
  @UseGuards(OptionalJwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Get problems similar to a specific problem' })
  @ApiParam({
    name: 'problemId',
    type: Number,
    description: 'Source problem ID',
  })
  @ApiQuery({
    name: 'size',
    required: false,
    type: Number,
    description: 'Number of recommendations (default: 5)',
  })
  @ApiResponse({
    status: 200,
    description: 'Similar problems retrieved successfully',
    type: RecommendResponseDto,
  })
  async getSimilarProblems(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Req() req: RequestWithOptionalUser,
    @Query('size') size?: number,
  ): Promise<RecommendResponseDto> {
    const userId = req.user?.id;
    if (!userId) {
      return {
        success: false,
        code: 401,
        message: 'Authentication required',
        data: null,
      };
    }

    return this.recommendationService.getSimilarProblems(
      userId,
      problemId,
      size ? Number(size) : 5,
    );
  }

  /**
   * Get weak point strengthening recommendations
   */
  @Get('weak-points')
  @UseGuards(OptionalJwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Get recommendations for weak point strengthening' })
  @ApiQuery({
    name: 'size',
    required: false,
    type: Number,
    description: 'Number of recommendations (default: 10)',
  })
  @ApiQuery({
    name: 'tags',
    required: false,
    type: String,
    description: 'Comma-separated target tags',
  })
  @ApiResponse({
    status: 200,
    description: 'Weak point recommendations retrieved successfully',
    type: RecommendResponseDto,
  })
  async getWeakPointRecommendations(
    @Req() req: RequestWithOptionalUser,
    @Query('size') size?: number,
    @Query('tags') tags?: string,
  ): Promise<RecommendResponseDto> {
    const userId = req.user?.id;
    if (!userId) {
      return {
        success: false,
        code: 401,
        message: 'Authentication required',
        data: null,
      };
    }

    const targetTags = tags ? tags.split(',').map((t) => t.trim()) : undefined;
    return this.recommendationService.getWeakPointRecommendations(
      userId,
      size ? Number(size) : 10,
      targetTags,
    );
  }

  /**
   * Get challenge mode recommendations
   */
  @Get('challenge')
  @UseGuards(OptionalJwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({
    summary: 'Get challenge mode recommendations (harder problems)',
  })
  @ApiQuery({
    name: 'size',
    required: false,
    type: Number,
    description: 'Number of recommendations (default: 5)',
  })
  @ApiResponse({
    status: 200,
    description: 'Challenge recommendations retrieved successfully',
    type: RecommendResponseDto,
  })
  async getChallengeRecommendations(
    @Req() req: RequestWithOptionalUser,
    @Query('size') size?: number,
  ): Promise<RecommendResponseDto> {
    const userId = req.user?.id;
    if (!userId) {
      return {
        success: false,
        code: 401,
        message: 'Authentication required',
        data: null,
      };
    }

    return this.recommendationService.getChallengeRecommendations(
      userId,
      size ? Number(size) : 5,
    );
  }

  /**
   * Health check endpoint for the recommendation service
   */
  @Get('health')
  @ApiOperation({ summary: 'Check recommendation service health' })
  @ApiResponse({
    status: 200,
    description: 'Health status retrieved',
  })
  async healthCheck() {
    return this.recommendationService.healthCheck();
  }
}
