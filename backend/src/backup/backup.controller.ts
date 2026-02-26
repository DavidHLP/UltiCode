import {
  Controller,
  Get,
  Post,
  Delete,
  Param,
  Body,
  Query,
  Res,
  UseGuards,
} from '@nestjs/common';
import {
  ApiTags,
  ApiOperation,
  ApiResponse,
  ApiBearerAuth,
} from '@nestjs/swagger';
import type { Response } from 'express';
import { BackupService } from './backup.service';
import {
  CreateBackupDto,
  RestoreBackupDto,
  BackupQueryDto,
  BackupResponseDto,
  BackupListResponseDto,
} from './dto/backup.dto';
import { AuthGuard } from '../auth/auth.guard';
import { CurrentUser } from '../auth/decorators/current-user.decorator';
import type { User } from '../user/user.service';
import * as fs from 'fs';

@ApiTags('Admin - Backup')
@ApiBearerAuth()
@UseGuards(AuthGuard)
@Controller('admin/backup')
export class BackupController {
  constructor(private readonly backupService: BackupService) {}

  @Post()
  @ApiOperation({ summary: 'Create a new backup' })
  @ApiResponse({
    status: 201,
    description: 'Backup created',
    type: BackupResponseDto,
  })
  async create(
    @Body() dto: CreateBackupDto,
    @CurrentUser() user: User,
  ): Promise<BackupResponseDto> {
    return this.backupService.createBackup(dto, user.id);
  }

  @Get()
  @ApiOperation({ summary: 'List all backups' })
  @ApiResponse({
    status: 200,
    description: 'List of backups',
    type: BackupListResponseDto,
  })
  async findAll(
    @Query() query: BackupQueryDto,
  ): Promise<BackupListResponseDto> {
    return this.backupService.findAll(query);
  }

  @Get(':id')
  @ApiOperation({ summary: 'Get backup details' })
  @ApiResponse({
    status: 200,
    description: 'Backup details',
    type: BackupResponseDto,
  })
  async findOne(@Param('id') id: string): Promise<BackupResponseDto> {
    return this.backupService.findOne(id);
  }

  @Get(':id/download')
  @ApiOperation({ summary: 'Download backup file' })
  @ApiResponse({ status: 200, description: 'Backup file' })
  async download(@Param('id') id: string, @Res() res: Response): Promise<void> {
    const filepath = await this.backupService.getBackupFilePath(id);
    const backup = await this.backupService.findOne(id);

    const stat = fs.statSync(filepath);
    const fileSize = stat.size;

    res.setHeader('Content-Length', fileSize);
    res.setHeader('Content-Type', 'application/octet-stream');
    res.setHeader(
      'Content-Disposition',
      `attachment; filename="${backup.filename}"`,
    );

    const fileStream = fs.createReadStream(filepath);
    fileStream.pipe(res);
  }

  @Post(':id/restore')
  @ApiOperation({ summary: 'Restore from backup' })
  @ApiResponse({ status: 200, description: 'Restore result' })
  async restore(
    @Param('id') id: string,
    @Body() dto: RestoreBackupDto,
  ): Promise<{ success: boolean }> {
    if (!dto.confirm) {
      throw new Error('Restore must be confirmed');
    }
    return this.backupService.restoreBackup(id);
  }

  @Delete(':id')
  @ApiOperation({ summary: 'Delete backup' })
  @ApiResponse({ status: 200, description: 'Backup deleted' })
  async remove(@Param('id') id: string): Promise<{ success: boolean }> {
    return this.backupService.remove(id);
  }
}
