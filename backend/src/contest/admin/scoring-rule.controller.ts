import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  Query,
  UseGuards,
} from '@nestjs/common';
import { ApiTags, ApiOperation, ApiBearerAuth } from '@nestjs/swagger';
import { ScoringRuleService } from '../services/scoring-rule.service';
import {
  CreateScoringRuleDto,
  UpdateScoringRuleDto,
} from '../dto/scoring-rule.dto';
import { JwtAuthGuard } from '../../auth/guards/jwt-auth.guard';
import { RolesGuard } from '../../auth/guards/roles.guard';
import { RequireRoles } from '../../admin/decorators/roles.decorator';
import { UserRole } from '@prisma/client';

@ApiTags('Admin / Scoring Rules')
@ApiBearerAuth()
@Controller('admin/scoring-rules')
@UseGuards(JwtAuthGuard, RolesGuard)
@RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
export class ScoringRuleController {
  constructor(private readonly scoringRuleService: ScoringRuleService) {}

  @Get()
  @ApiOperation({ summary: 'Get all scoring rules' })
  async findAll(@Query('includeInactive') includeInactive?: string) {
    return this.scoringRuleService.findAll(includeInactive === 'true');
  }

  @Get(':id')
  @ApiOperation({ summary: 'Get scoring rule by ID' })
  async findOne(@Param('id') id: string) {
    return this.scoringRuleService.findOne(id);
  }

  @Post()
  @ApiOperation({ summary: 'Create a new scoring rule' })
  async create(@Body() dto: CreateScoringRuleDto) {
    return this.scoringRuleService.create(dto);
  }

  @Put(':id')
  @ApiOperation({ summary: 'Update a scoring rule' })
  async update(@Param('id') id: string, @Body() dto: UpdateScoringRuleDto) {
    return this.scoringRuleService.update(id, dto);
  }

  @Delete(':id')
  @ApiOperation({ summary: 'Delete a scoring rule' })
  async remove(@Param('id') id: string) {
    return this.scoringRuleService.remove(id);
  }
}
