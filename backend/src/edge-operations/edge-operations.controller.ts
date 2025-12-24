import {
  Body,
  Controller,
  Get,
  Param,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { EdgeOperationsService } from './edge-operations.service';
import { EdgeOperationDto } from './dto/edge-operation.dto';
import { AuthGuard } from '../auth/auth.guard';
import type { Request } from 'express';
import { EdgeOperationTargetType } from '@prisma/client';

interface AuthenticatedRequest extends Request {
  user: { id: string };
}

@Controller('edge-operations')
export class EdgeOperationsController {
  constructor(private readonly edgeOperationsService: EdgeOperationsService) {}

  @Get(':targetType/:targetId')
  async getInteractions(
    @Param('targetType') targetType: EdgeOperationTargetType,
    @Param('targetId') targetId: string,
    @Query('userId') userId?: string,
  ) {
    return this.edgeOperationsService.getInteractions(
      targetType,
      targetId,
      userId,
    );
  }

  @UseGuards(AuthGuard)
  @Post()
  async operate(
    @Body() dto: EdgeOperationDto,
    @Req() req: AuthenticatedRequest,
  ) {
    return this.edgeOperationsService.operate(req.user.id, dto);
  }
}
