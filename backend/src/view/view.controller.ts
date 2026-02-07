import {
  Body,
  Controller,
  Ip,
  Param,
  Post,
  UseFilters,
  UseInterceptors,
} from '@nestjs/common';
import { ViewService } from './view.service';
import { ViewTargetType } from '@prisma/client';
import { RecordViewDto } from './dto/record-view.dto';
import { ResponseInterceptor } from '../common/interceptors/response.interceptor';
import { GlobalExceptionFilter } from '../common/filters/global-exception.filter';

@Controller('views')
export class ViewController {
  constructor(private readonly viewService: ViewService) {}

  @UseInterceptors(ResponseInterceptor)
  @UseFilters(GlobalExceptionFilter)
  @Post()
  async recordView(@Body() dto: RecordViewDto, @Ip() ip?: string) {
    return this.viewService.recordView(
      dto.targetType,
      dto.targetId,
      dto.userId,
      ip,
    );
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
