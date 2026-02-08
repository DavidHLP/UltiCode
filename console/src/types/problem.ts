// Import shared types
import type { ProblemEntity, Difficulty } from "@ulticode/shared-types";

export interface Problem extends Omit<ProblemEntity, "id" | "difficulty"> {
  id: number; // Console uses number, shared uses string - keeping console type for compatibility
  difficulty: Difficulty; // Using shared Difficulty type
  acceptanceRate?: number; // alias or computed
  tags: string[]; // Console-specific field
  status?: "solved" | "attempted" | "todo"; // Console-specific field
  isPremium?: boolean; // Maps to is_premium
  hasSolution?: boolean; // Console-specific field
  completedTime?: string; // Frontend specific
}

// Re-export shared types for convenience
export type { ProblemEntity, Difficulty } from "@ulticode/shared-types";
