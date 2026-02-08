/**
 * Bookmark metadata type definitions
 *
 * Provides discriminated union types for bookmark metadata based on BookmarkType.
 */

import { BookmarkType } from "./bookmark";

/**
 * Metadata for PROBLEM bookmarks
 */
export interface ProblemBookmarkMetadata {
  difficulty: string;
  tags: string[];
  acceptanceRate: number;
  solvedAt?: Date;
  [key: string]: unknown; // Index signature for type guard compatibility
}

/**
 * Metadata for SOLUTION bookmarks
 */
export interface SolutionBookmarkMetadata {
  language: string;
  runtime: number;
  memory: number;
  isAccepted: boolean;
  bookmarkedAt: Date;
  [key: string]: unknown; // Index signature for type guard compatibility
}

/**
 * Metadata for FORUM_POST bookmarks
 */
export interface ForumPostBookmarkMetadata {
  category: string;
  authorId: string;
  upvotes: number;
  bookmarkedAt: Date;
  [key: string]: unknown; // Index signature for type guard compatibility
}

/**
 * Metadata for PROBLEM_LIST bookmarks
 */
export interface ProblemListBookmarkMetadata {
  problemCount: number;
  authorId: string;
  isFeatured: boolean;
  [key: string]: unknown; // Index signature for type guard compatibility
}

/**
 * Metadata for SOLUTION_COMMENT bookmarks
 */
export interface SolutionCommentBookmarkMetadata {
  solutionId: string;
  authorId: string;
  upvotes: number;
  bookmarkedAt: Date;
  [key: string]: unknown; // Index signature for type guard compatibility
}

/**
 * Metadata for FORUM_COMMENT bookmarks
 */
export interface ForumCommentBookmarkMetadata {
  postId: string;
  authorId: string;
  upvotes: number;
  bookmarkedAt: Date;
  [key: string]: unknown; // Index signature for type guard compatibility
}

/**
 * Discriminated union for all bookmark metadata types
 */
export type BookmarkMetadata = {
  [BookmarkType.PROBLEM]: ProblemBookmarkMetadata;
  [BookmarkType.SOLUTION]: SolutionBookmarkMetadata;
  [BookmarkType.FORUM_POST]: ForumPostBookmarkMetadata;
  [BookmarkType.PROBLEM_LIST]: ProblemListBookmarkMetadata;
  [BookmarkType.SOLUTION_COMMENT]: SolutionCommentBookmarkMetadata;
  [BookmarkType.FORUM_COMMENT]: ForumCommentBookmarkMetadata;
}[BookmarkType];

/**
 * Type guard for ProblemBookmarkMetadata
 */
export function isProblemBookmarkMetadata(
  metadata: Record<string, unknown>,
): metadata is ProblemBookmarkMetadata {
  return (
    typeof metadata.difficulty === "string" &&
    Array.isArray(metadata.tags) &&
    typeof metadata.acceptanceRate === "number"
  );
}

/**
 * Type guard for SolutionBookmarkMetadata
 */
export function isSolutionBookmarkMetadata(
  metadata: Record<string, unknown>,
): metadata is SolutionBookmarkMetadata {
  return (
    typeof metadata.language === "string" &&
    typeof metadata.runtime === "number" &&
    typeof metadata.memory === "number" &&
    typeof metadata.isAccepted === "boolean"
  );
}

/**
 * Type guard for ForumPostBookmarkMetadata
 */
export function isForumPostBookmarkMetadata(
  metadata: Record<string, unknown>,
): metadata is ForumPostBookmarkMetadata {
  return (
    typeof metadata.category === "string" &&
    typeof metadata.authorId === "string" &&
    typeof metadata.upvotes === "number"
  );
}

/**
 * Type guard for ProblemListBookmarkMetadata
 */
export function isProblemListBookmarkMetadata(
  metadata: Record<string, unknown>,
): metadata is ProblemListBookmarkMetadata {
  return (
    typeof metadata.problemCount === "number" &&
    typeof metadata.authorId === "string" &&
    typeof metadata.isFeatured === "boolean"
  );
}

/**
 * Type guard for SolutionCommentBookmarkMetadata
 */
export function isSolutionCommentBookmarkMetadata(
  metadata: Record<string, unknown>,
): metadata is SolutionCommentBookmarkMetadata {
  return (
    typeof metadata.solutionId === "string" &&
    typeof metadata.authorId === "string" &&
    typeof metadata.upvotes === "number"
  );
}

/**
 * Type guard for ForumCommentBookmarkMetadata
 */
export function isForumCommentBookmarkMetadata(
  metadata: Record<string, unknown>,
): metadata is ForumCommentBookmarkMetadata {
  return (
    typeof metadata.postId === "string" &&
    typeof metadata.authorId === "string" &&
    typeof metadata.upvotes === "number"
  );
}

/**
 * Generic metadata type guard based on bookmark type
 */
export function isBookmarkMetadata(
  metadata: Record<string, unknown>,
  type: BookmarkType,
): metadata is BookmarkMetadata {
  switch (type) {
    case BookmarkType.PROBLEM:
      return isProblemBookmarkMetadata(metadata);
    case BookmarkType.SOLUTION:
      return isSolutionBookmarkMetadata(metadata);
    case BookmarkType.FORUM_POST:
      return isForumPostBookmarkMetadata(metadata);
    case BookmarkType.PROBLEM_LIST:
      return isProblemListBookmarkMetadata(metadata);
    case BookmarkType.SOLUTION_COMMENT:
      return isSolutionCommentBookmarkMetadata(metadata);
    case BookmarkType.FORUM_COMMENT:
      return isForumCommentBookmarkMetadata(metadata);
    default:
      return false;
  }
}
