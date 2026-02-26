import { Module } from '@nestjs/common';
import { BackupController } from './backup.controller';
import { BackupService } from './backup.service';
import { PrismaService } from '../prisma.service';

@Module({
  controllers: [BackupController],
  providers: [BackupService, PrismaService],
  exports: [BackupService],
})
export class BackupModule {}
