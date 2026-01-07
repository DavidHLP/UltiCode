import { Injectable, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { PrismaClient } from '@prisma/client';

@Injectable()
export class PrismaService
  extends PrismaClient
  implements OnModuleInit, OnModuleDestroy
{
  constructor() {
    super({
      datasources: {
        db: {
          url: process.env.DATABASE_URL,
        },
      },
    });
  }

  async onModuleInit() {
    await this.$connect();

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
                where?: unknown;
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
