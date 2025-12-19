import { Body, Controller, Post, Req, UseGuards } from '@nestjs/common';
import { VoteService } from './vote.service';
import { VoteDto } from './dto/vote.dto';
import { AuthGuard } from '../auth/auth.guard';
import type { Request } from 'express';

interface AuthenticatedRequest extends Request {
  user: { id: string };
}

@Controller('votes')
export class VoteController {
  constructor(private readonly voteService: VoteService) {}

  @UseGuards(AuthGuard)
  @Post()
  async vote(@Body() dto: VoteDto, @Req() req: AuthenticatedRequest) {
    const user = req.user;
    return await this.voteService.vote(user.id, dto);
  }
}
