import { Module } from '@nestjs/common';
import { EmailController } from './email.controller';
import { EmailService } from './email.service';
import { PrismaService } from '../prisma.service';

@Module({
  controllers: [EmailController],
  providers: [EmailService, PrismaService],
  exports: [EmailService],
})
export class EmailModule {}
