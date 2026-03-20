import { EdgeOperationType, EdgeOperationTargetType } from '@prisma/client';

export const EDGE_OPERATION_TYPES = Object.values(EdgeOperationType);
export const EDGE_OPERATION_TARGET_TYPES = Object.values(EdgeOperationTargetType);

export const OPERATION_WEIGHTS: Record<EdgeOperationType, number> = {
  [EdgeOperationType.VOTE_UP]: 75,    // 75% chance
  [EdgeOperationType.VOTE_DOWN]: 15,  // 15% chance
  [EdgeOperationType.ANALYZE]: 10,    // 10% chance
};

// Probability of a user performing an operation on a target they see
export const OPERATION_PROBABILITY = 0.3;

export const MAX_OPERATIONS_PER_USER = 50;
