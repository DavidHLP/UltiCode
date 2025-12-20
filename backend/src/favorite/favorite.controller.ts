import { Body, Controller, Post, Req, UseGuards } from '@nestjs/common';
import { FavoriteService } from './favorite.service';
import { ToggleFavoriteDto } from './dto/toggle-favorite.dto';
import { AuthGuard } from '../auth/auth.guard';
import type { Request } from 'express';

interface AuthenticatedRequest extends Request {
  user: { id: string };
}

@Controller('favorites')
export class FavoriteController {
  constructor(private readonly favoriteService: FavoriteService) {}

  @UseGuards(AuthGuard)
  @Post('toggle')
  async toggle(
    @Body() dto: ToggleFavoriteDto,
    @Req() req: AuthenticatedRequest,
  ) {
    return this.favoriteService.toggle(
      req.user.id,
      dto.targetType,
      dto.targetId,
    );
  }
}
