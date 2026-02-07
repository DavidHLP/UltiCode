import type { Prisma } from '@prisma/client';

interface LikeObject {
  constructor: { name: string };
  parameter: string;
}

interface WhereCondition {
  [key: string]: unknown;
}

/**
 * Transforms TypeORM-like query conditions to Prisma-compatible format.
 *
 * This utility handles TypeORM OR conditions and Like objects, converting them
 * to Prisma's expected query format. It's primarily used to maintain compatibility
 * with legacy TypeORM-style query builders.
 *
 * @param where - TypeORM-like where condition or array of OR conditions
 * @returns Prisma-compatible where input
 */
export function transformWhereCondition(
  where?: Prisma.UserWhereInput,
): Prisma.UserWhereInput {
  const prismaWhere: Prisma.UserWhereInput = {};

  if (!where) {
    return prismaWhere;
  }

  if (Array.isArray(where)) {
    // Handle TypeORM OR conditions - convert to Prisma format
    // For search queries with Like, we need to extract the actual values
    const orConditions = where.map((w: WhereCondition) => {
      const condition: WhereCondition = {};
      for (const [key, value] of Object.entries(w)) {
        // Handle TypeORM Like objects
        if (
          value &&
          typeof value === 'object' &&
          'constructor' in value &&
          (value as LikeObject).constructor?.name === 'Like'
        ) {
          condition[key] = (value as LikeObject).parameter;
        } else {
          condition[key] = value;
        }
      }
      return condition;
    });
    prismaWhere.OR = orConditions as Prisma.UserWhereInput['OR'];
  } else {
    Object.assign(prismaWhere, where);
  }

  return prismaWhere;
}
