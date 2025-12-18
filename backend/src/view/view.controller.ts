import { Controller, Post, Param, Ip, Body } from '@nestjs/common';
import { ViewService } from './view.service';
import { ViewTargetType } from '@prisma/client';

@Controller('views')
export class ViewController {
  constructor(private readonly viewService: ViewService) {}

  @Post()
  async recordView(
    @Body('targetType') targetType: ViewTargetType,
    @Body('targetId') targetId: string,
    @Body('userId') bodyUserId?: string,
    @Ip() ip?: string,
  ) {
    return this.viewService.recordView(targetType, targetId, bodyUserId, ip);
  }

  @Post('solution/:id')
  async recordSolutionView(
    @Param('id') id: string,
    @Ip() ip: string,
    @Body('userId') bodyUserId?: string,
  ) {
    return this.viewService.recordView(
      ViewTargetType.SOLUTION,
      id,
      bodyUserId,
      ip,
    );
  }

  @Post('forum/:id')
  async recordForumView(
    @Param('id') id: string,
    @Ip() ip: string,
    @Body('userId') bodyUserId?: string,
  ) {
    return this.viewService.recordView(
      ViewTargetType.FORUM_POST,
      id,
      bodyUserId,
      ip,
    );
  }
}
