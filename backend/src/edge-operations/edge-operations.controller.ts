import { Body, Controller, Post, Req, UseGuards } from '@nestjs/common';
import { EdgeOperationsService } from './edge-operations.service';
import { EdgeOperationDto } from './dto/edge-operation.dto';
import { AuthGuard } from '../auth/auth.guard';
import type { Request } from 'express';

interface AuthenticatedRequest extends Request {
  user: { id: string };
}

@Controller('edge-operations')
export class EdgeOperationsController {
  constructor(private readonly edgeOperationsService: EdgeOperationsService) {}

  @UseGuards(AuthGuard)
  @Post()
  async operate(
    @Body() dto: EdgeOperationDto,
    @Req() req: AuthenticatedRequest,
  ) {
    return this.edgeOperationsService.operate(req.user.id, dto);
  }
}
