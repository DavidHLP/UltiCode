import { Injectable, Logger, NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Cron, CronExpression } from '@nestjs/schedule';
import { PrismaService } from '../prisma.service';
import {
  CreateBackupDto,
  BackupQueryDto,
  BackupResponseDto,
  BackupListResponseDto,
  BackupType,
  BackupStatus,
} from './dto/backup.dto';
import { exec } from 'child_process';
import { promisify } from 'util';
import * as fs from 'fs';
import * as path from 'path';

const execAsync = promisify(exec);

@Injectable()
export class BackupService {
  private readonly logger = new Logger(BackupService.name);
  private readonly backupDir: string;

  constructor(
    private prisma: PrismaService,
    private configService: ConfigService,
  ) {
    this.backupDir = this.configService.get('BACKUP_DIR', '/tmp/backups');
    this.ensureBackupDir();
  }

  private ensureBackupDir(): void {
    if (!fs.existsSync(this.backupDir)) {
      fs.mkdirSync(this.backupDir, { recursive: true });
    }
  }

  async createBackup(
    dto: CreateBackupDto,
    userId: string,
  ): Promise<BackupResponseDto> {
    const id = `backup_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    const filename = `${id}.sql`;

    // Create backup record
    const backup = await this.prisma.backup.create({
      data: {
        id,
        filename,
        size: BigInt(0),
        type: dto.type ?? BackupType.FULL,
        status: BackupStatus.PENDING,
        created_by: userId,
        metadata: { description: dto.description },
      },
    });

    // Execute backup asynchronously
    this.executeBackup(backup.id, filename).catch((error) => {
      this.logger.error(`Backup ${backup.id} failed:`, error);
    });

    return this.toResponse(backup);
  }

  private async executeBackup(id: string, filename: string): Promise<void> {
    try {
      // Update status to in progress
      await this.prisma.backup.update({
        where: { id },
        data: { status: BackupStatus.IN_PROGRESS },
      });

      const dbHost = this.configService.get('DB_HOST', 'localhost');
      const dbPort = this.configService.get('DB_PORT', '3306');
      const dbName = this.configService.get('DB_NAME', 'ulticode');
      const dbUser = this.configService.get('DB_USER', 'root');
      const dbPassword = this.configService.get('DB_PASSWORD', '');

      const filepath = path.join(this.backupDir, filename);
      const env = { ...process.env, MYSQL_PWD: dbPassword };

      // Execute mysqldump
      const { stderr } = await execAsync(
        `mysqldump -h ${dbHost} -P ${dbPort} -u ${dbUser} ${dbName} > ${filepath}`,
        { env, shell: '/bin/bash' },
      );

      if (stderr && !stderr.includes('Warning')) {
        throw new Error(stderr);
      }

      // Get file size
      const stats = fs.statSync(filepath);
      const size = stats.size;

      // Update backup record
      await this.prisma.backup.update({
        where: { id },
        data: {
          size: BigInt(size),
          status: BackupStatus.COMPLETED,
          completed_at: new Date(),
        },
      });

      this.logger.log(`Backup ${id} completed successfully`);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';

      await this.prisma.backup.update({
        where: { id },
        data: {
          status: BackupStatus.FAILED,
          error: message,
          completed_at: new Date(),
        },
      });

      throw error;
    }
  }

  async findAll(query: BackupQueryDto): Promise<BackupListResponseDto> {
    const where: any = {};

    if (query.status) {
      where.status = query.status;
    }

    if (query.type) {
      where.type = query.type;
    }

    const page = query.page ?? 1;
    const limit = query.limit ?? 20;

    const [total, items] = await Promise.all([
      this.prisma.backup.count({ where }),
      this.prisma.backup.findMany({
        where,
        orderBy: { created_at: 'desc' },
        skip: (page - 1) * limit,
        take: limit,
      }),
    ]);

    return {
      items: items.map((b) => this.toResponse(b)),
      total,
      page,
      limit,
    };
  }

  async findOne(id: string): Promise<BackupResponseDto> {
    const backup = await this.prisma.backup.findUnique({
      where: { id },
    });

    if (!backup) {
      throw new NotFoundException(`Backup ${id} not found`);
    }

    return this.toResponse(backup);
  }

  async getBackupFilePath(id: string): Promise<string> {
    const backup = await this.findOne(id);

    if (backup.status !== BackupStatus.COMPLETED) {
      throw new Error('Backup is not completed');
    }

    const filepath = path.join(this.backupDir, backup.filename);

    if (!fs.existsSync(filepath)) {
      throw new NotFoundException('Backup file not found');
    }

    return filepath;
  }

  async restoreBackup(id: string): Promise<{ success: boolean }> {
    const backup = await this.findOne(id);

    if (backup.status !== BackupStatus.COMPLETED) {
      throw new Error('Cannot restore a backup that is not completed');
    }

    const filepath = path.join(this.backupDir, backup.filename);

    if (!fs.existsSync(filepath)) {
      throw new NotFoundException('Backup file not found');
    }

    const dbHost = this.configService.get('DB_HOST', 'localhost');
    const dbPort = this.configService.get('DB_PORT', '3306');
    const dbName = this.configService.get('DB_NAME', 'ulticode');
    const dbUser = this.configService.get('DB_USER', 'root');
    const dbPassword = this.configService.get('DB_PASSWORD', '');

    const env = { ...process.env, MYSQL_PWD: dbPassword };

    try {
      // Execute mysql restore
      await execAsync(
        `mysql -h ${dbHost} -P ${dbPort} -u ${dbUser} ${dbName} < ${filepath}`,
        { env, shell: '/bin/bash' },
      );

      this.logger.log(`Backup ${id} restored successfully`);
      return { success: true };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      this.logger.error(`Restore failed: ${message}`);
      throw new Error(`Restore failed: ${message}`);
    }
  }

  async remove(id: string): Promise<{ success: boolean }> {
    const backup = await this.findOne(id);

    // Delete file
    const filepath = path.join(this.backupDir, backup.filename);
    if (fs.existsSync(filepath)) {
      fs.unlinkSync(filepath);
    }

    // Delete record
    await this.prisma.backup.delete({
      where: { id },
    });

    return { success: true };
  }

  @Cron(CronExpression.EVERY_DAY_AT_2AM)
  async scheduledBackup(): Promise<void> {
    this.logger.log('Starting scheduled backup...');

    try {
      await this.createBackup({ type: BackupType.FULL }, 'system');
      this.logger.log('Scheduled backup completed');
    } catch (error) {
      this.logger.error('Scheduled backup failed:', error);
    }
  }

  private toResponse(backup: any): BackupResponseDto {
    return {
      id: backup.id,
      filename: backup.filename,
      size: Number(backup.size),
      type: backup.type,
      status: backup.status,
      created_by: backup.created_by,
      created_at: backup.created_at,
      completed_at: backup.completed_at,
      error: backup.error,
      metadata: backup.metadata,
    };
  }
}
