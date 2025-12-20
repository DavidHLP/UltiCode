import { Module } from '@nestjs/common';
import { FavoriteService } from './favorite.service';
import { FavoriteController } from './favorite.controller';
import { PrismaService } from '../prisma.service';

@Module({
  controllers: [FavoriteController],
  providers: [FavoriteService, PrismaService],
  exports: [FavoriteService],
})
export class FavoriteModule {}
