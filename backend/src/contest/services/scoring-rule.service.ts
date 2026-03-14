import {
  Injectable,
  NotFoundException,
  BadRequestException,
  Logger,
} from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import {
  CreateScoringRuleDto,
  UpdateScoringRuleDto,
} from '../dto/scoring-rule.dto';
import { CONTEST_ERRORS } from '../../common/constants/contest-errors';

@Injectable()
export class ScoringRuleService {
  private readonly logger = new Logger(ScoringRuleService.name);

  constructor(private readonly prisma: PrismaService) {}

  /**
   * Get all active scoring rules
   */
  async findAll(includeInactive = false) {
    return this.prisma.contestScoringRule.findMany({
      where: includeInactive ? undefined : { is_active: true },
      orderBy: [{ is_default: 'desc' }, { created_at: 'asc' }],
    });
  }

  /**
   * Get scoring rule by ID
   */
  async findOne(id: string) {
    const rule = await this.prisma.contestScoringRule.findUnique({
      where: { id },
    });

    if (!rule) {
      throw new NotFoundException(CONTEST_ERRORS.SCORING_RULE_NOT_FOUND.message);
    }

    return rule;
  }

  /**
   * Get the default scoring rule
   */
  async findDefault() {
    const rule = await this.prisma.contestScoringRule.findFirst({
      where: { is_default: true, is_active: true },
    });

    if (rule) {
      return rule;
    }

    // Fallback to first active rule
    return this.prisma.contestScoringRule.findFirst({
      where: { is_active: true },
    });
  }

  /**
   * Create a new scoring rule
   */
  async create(dto: CreateScoringRuleDto) {
    // If this is set as default, unset other defaults
    if (dto.is_default) {
      await this.prisma.contestScoringRule.updateMany({
        where: { is_default: true },
        data: { is_default: false },
      });
    }

    return this.prisma.contestScoringRule.create({
      data: {
        name: dto.name,
        description: dto.description,
        base_score_per_problem: dto.base_score_per_problem,
        time_bonus_per_minute: dto.time_bonus_per_minute,
        wrong_answer_penalty: dto.wrong_answer_penalty,
        time_limit_penalty: dto.time_limit_penalty ?? 0,
        first_solve_bonus: dto.first_solve_bonus,
        full_score_bonus: dto.full_score_bonus ?? 0,
        is_default: dto.is_default ?? false,
        is_active: true,
      },
    });
  }

  /**
   * Update a scoring rule
   */
  async update(id: string, dto: UpdateScoringRuleDto) {
    // Check rule exists
    await this.findOne(id);

    // If setting as default, unset other defaults
    if (dto.is_default) {
      await this.prisma.contestScoringRule.updateMany({
        where: { is_default: true, id: { not: id } },
        data: { is_default: false },
      });
    }

    return this.prisma.contestScoringRule.update({
      where: { id },
      data: {
        ...(dto.name !== undefined && { name: dto.name }),
        ...(dto.description !== undefined && { description: dto.description }),
        ...(dto.base_score_per_problem !== undefined && {
          base_score_per_problem: dto.base_score_per_problem,
        }),
        ...(dto.time_bonus_per_minute !== undefined && {
          time_bonus_per_minute: dto.time_bonus_per_minute,
        }),
        ...(dto.wrong_answer_penalty !== undefined && {
          wrong_answer_penalty: dto.wrong_answer_penalty,
        }),
        ...(dto.time_limit_penalty !== undefined && {
          time_limit_penalty: dto.time_limit_penalty,
        }),
        ...(dto.first_solve_bonus !== undefined && {
          first_solve_bonus: dto.first_solve_bonus,
        }),
        ...(dto.full_score_bonus !== undefined && {
          full_score_bonus: dto.full_score_bonus,
        }),
        ...(dto.is_default !== undefined && { is_default: dto.is_default }),
      },
    });
  }

  /**
   * Delete a scoring rule
   * Performs soft delete if used by contests
   */
  async remove(id: string) {
    const rule = await this.findOne(id);

    // Cannot delete default rule
    if (rule.is_default) {
      throw new BadRequestException(
        CONTEST_ERRORS.CANNOT_DELETE_DEFAULT_RULE.message,
      );
    }

    // Check if rule is used by any contests
    const usageCount = await this.prisma.contest.count({
      where: { scoring_rule_id: id },
    });

    if (usageCount > 0) {
      // Soft delete - just mark as inactive
      this.logger.log(
        `Soft deleting rule ${id} as it is used by ${usageCount} contests`,
      );
      return this.prisma.contestScoringRule.update({
        where: { id },
        data: { is_active: false },
      });
    }

    // Hard delete
    return this.prisma.contestScoringRule.delete({
      where: { id },
    });
  }
}
