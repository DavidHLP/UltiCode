import {
  Injectable,
  OnModuleInit,
  OnModuleDestroy,
  Logger,
  InternalServerErrorException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PrismaClient } from '@prisma/client';

@Injectable()
export class PrismaService
  extends PrismaClient
  implements OnModuleInit, OnModuleDestroy
{
  private readonly logger = new Logger(PrismaService.name);

  constructor(private configService: ConfigService) {
    super({
      datasources: {
        db: {
          url: configService.get<string>('DATABASE_URL'),
        },
      },
      log: ['error', 'warn'],
      errorFormat: 'pretty',
    });

    // Handle Prisma errors
    // @ts-expect-error - Prisma $on type definitions are incomplete
    this.$on('error', (error: Error) => {
      this.logger.error(`Prisma error: ${error.message}`, error.stack);
    });
  }

  async onModuleInit() {
    try {
      await this.$connect();
      this.logger.log('Database connected');
    } catch (error) {
      this.logger.error('Failed to connect to database', error);
      throw new InternalServerErrorException('Database connection failed');
    }

    // Use $extends for soft delete middleware replacement
    // eslint-disable-next-line @typescript-eslint/no-this-alias
    const prismaClient = this;
    const extendedClient = this.$extends({
      query: {
        $allModels: {
          async $allOperations({ model, operation, args, query }) {
            const softDeleteModels = [
              'Problem',
              'Contest',
              'ForumPost',
              'ForumComment',
              'Solution',
              'SolutionComment',
            ];

            if (
              model &&
              typeof model === 'string' &&
              softDeleteModels.includes(model)
            ) {
              const argsWithData = args as {
                where?: Record<string, unknown>;
                data?: Record<string, unknown>;
              };
              if (operation === 'delete') {
                argsWithData.data = {
                  is_deleted: true,
                  deleted_at: new Date(),
                };
                return (
                  prismaClient[
                    model.toLowerCase() as keyof typeof prismaClient
                  ] as { update: (args: unknown) => Promise<unknown> }
                ).update(argsWithData);
              }
              if (operation === 'deleteMany') {
                if (argsWithData.data !== undefined) {
                  argsWithData.data['is_deleted'] = true;
                  argsWithData.data['deleted_at'] = new Date();
                } else {
                  argsWithData.data = {
                    is_deleted: true,
                    deleted_at: new Date(),
                  };
                }
                return (
                  prismaClient[
                    model.toLowerCase() as keyof typeof prismaClient
                  ] as { updateMany: (args: unknown) => Promise<unknown> }
                ).updateMany(argsWithData);
              }

              // Filter out soft-deleted records in read operations
              if (
                ['findUnique', 'findFirst', 'findMany', 'count'].includes(
                  operation,
                )
              ) {
                if (!argsWithData.where) {
                  argsWithData.where = {};
                }
                // Only filter if is_deleted filter is not already set
                // This allows explicit queries for deleted records when needed
                if (!('is_deleted' in argsWithData.where)) {
                  argsWithData.where.is_deleted = false;
                }
              }
            }
            return query(args);
          },
        },
      },
    });

    Object.assign(this, extendedClient);
  }

  async onModuleDestroy() {
    await this.$disconnect();
  }
}
