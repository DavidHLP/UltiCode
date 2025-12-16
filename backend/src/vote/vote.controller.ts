import { Body, Controller, Post, Query } from '@nestjs/common';
import { VoteService } from './vote.service';
import { VoteDto } from './dto/vote.dto';

@Controller('votes')
export class VoteController {
  constructor(private readonly voteService: VoteService) {}

  @Post()
  async vote(
    @Body() dto: VoteDto,
    @Query('userId') userId: string, // TODO: Get from Auth Guard
  ) {
    // Fallback for dev purposes if no Auth Guard yet
    const effectiveUserId = userId || 'u-001';
    return await this.voteService.vote(effectiveUserId, dto);
  }
}
