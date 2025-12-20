import {
  Body,
  Controller,
  Get,
  Param,
  ParseIntPipe,
  Post,
  Req,
  UseGuards,
} from '@nestjs/common';
import { ProblemNoteService } from './note.service';
import { SaveNoteDto } from './dto/save-note.dto';
import { AuthGuard } from '../../auth/auth.guard';
import type { Request } from 'express';

interface AuthenticatedRequest extends Request {
  user: { id: string };
}

@Controller('problems/:problemId/note')
export class ProblemNoteController {
  constructor(private readonly noteService: ProblemNoteService) {}

  @UseGuards(AuthGuard)
  @Post()
  async save(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Body() dto: SaveNoteDto,
    @Req() req: AuthenticatedRequest,
  ) {
    return this.noteService.save(req.user.id, problemId, dto.content);
  }

  @UseGuards(AuthGuard)
  @Get()
  async findOne(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Req() req: AuthenticatedRequest,
  ) {
    return this.noteService.findByProblem(req.user.id, problemId);
  }
}
