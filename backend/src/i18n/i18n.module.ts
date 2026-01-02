import { Module, Global } from '@nestjs/common';
import { I18nService } from './i18n.service';
import { PrismaService } from '../prisma.service';

@Global()
@Module({
  providers: [I18nService, PrismaService],
  exports: [I18nService],
})
export class I18nModule {}
