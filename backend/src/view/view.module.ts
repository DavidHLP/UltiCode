import { Module } from '@nestjs/common';
import { ViewService } from './view.service';
import { ViewController } from './view.controller';
import { PrismaService } from '../prisma.service';

@Module({
  controllers: [ViewController],
  providers: [ViewService, PrismaService],
  exports: [ViewService],
})
export class ViewModule {}
