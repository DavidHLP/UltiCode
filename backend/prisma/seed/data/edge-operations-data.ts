import { EdgeOperationType, EdgeOperationTargetType } from '@prisma/client';

export const EDGE_OPERATION_TYPES = Object.values(EdgeOperationType);
export const EDGE_OPERATION_TARGET_TYPES = Object.values(EdgeOperationTargetType);

export const OPERATION_WEIGHTS: Record<EdgeOperationType, number> = {
  [EdgeOperationType.VOTE_UP]: 70,    // 70% chance
  [EdgeOperationType.VOTE_DOWN]: 10,  // 10% chance
  [EdgeOperationType.FAVORITE]: 12,   // 12% chance
  [EdgeOperationType.ANALYZE]: 8,     // 8% chance
};

// Probability of a user performing an operation on a target they see
export const OPERATION_PROBABILITY = 0.3;

export const MAX_OPERATIONS_PER_USER = 50;
