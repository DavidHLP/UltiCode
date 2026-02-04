import {
  Controller,
  Get,
  Param,
  Query,
  Post,
  Body,
  Req,
  UseGuards,
} from '@nestjs/common';
import { Request } from 'express';
import { SolutionService } from './solution.service';
import type { SolutionFeedResponse } from './dto/solution-feed.dto';
import { CreateSolutionDto } from './dto/create-solution.dto';
import { AuthGuard } from '../auth/auth.guard';
import type { User } from '../user/user.service';

interface RequestWithUser extends Request {
  user: User;
}

@Controller('problems')
export class SolutionController {
  constructor(private readonly solutionService: SolutionService) {}

  @Get(':id/solutions')
  findSolutions(
    @Param('id') id: string,
    @Query('userId') userId?: string,
  ): Promise<SolutionFeedResponse> {
    return this.solutionService.findByProblemId(id, userId);
  }

  @Post(':id/solutions')
  @UseGuards(AuthGuard)
  create(
    @Param('id') id: string,
    @Body() dto: CreateSolutionDto,
    @Req() req: RequestWithUser,
  ) {
    return this.solutionService.create(id, req.user.id, dto);
  }
}
