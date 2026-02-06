import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { VoteService } from '../vote/vote.service';
import type { CreateSolutionDto } from './dto/create-solution.dto';
import type { CreateSolutionCommentDto } from './dto/create-solution-comment.dto';
import { SolutionCrudService } from './services/solution-crud.service';
import { SolutionQueryService } from './services/solution-query.service';
import { SolutionCommentService } from './services/solution-comment.service';

@Injectable()
export class SolutionService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly voteService: VoteService,
    private readonly crudService: SolutionCrudService,
    private readonly queryService: SolutionQueryService,
    private readonly commentService: SolutionCommentService,
  ) {}

  async create(problemId: string, userId: string, dto: CreateSolutionDto) {
    return this.crudService.create(problemId, userId, dto);
  }

  async findByProblemId(problemId: string, userId?: string) {
    return this.queryService.findByProblemId(problemId, userId);
  }

  async findAllByUser(userId: string, problemId?: string) {
    return this.queryService.findAllByUser(userId, problemId);
  }

  async findComments(solutionId: string, userId?: string) {
    return this.commentService.findComments(solutionId, userId);
  }

  async createComment(
    solutionId: string,
    dto: CreateSolutionCommentDto,
    userId: string,
  ) {
    return this.commentService.createComment(solutionId, dto, userId);
  }

  async findOne(id: string, userId?: string) {
    return this.queryService.findOne(id, userId);
  }

  async delete(id: string, userId: string) {
    return this.crudService.delete(id, userId);
  }

  async update(id: string, userId: string, dto: CreateSolutionDto) {
    return this.crudService.update(id, userId, dto);
  }

  async updateComment(commentId: string, content: string, userId: string) {
    return this.commentService.updateComment(commentId, content, userId);
  }

  async deleteComment(commentId: string, userId: string) {
    return this.commentService.deleteComment(commentId, userId);
  }
}
