import type { Comment, SolutionComment, ForumComment } from "@/types/comment";
import { formatRelativeTime } from "@/utils/datetime";
import { resolveUserVote, resolveVoteCounts } from "@/utils/vote";

interface BuildTreeOptions {
  postAuthorUsername?: string;
  currentUserId?: string;
}

const buildAvatar = (username: string, avatar?: string) =>
  avatar ||
  `https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(username)}`;

const mapToComment = (
  input: ForumComment,
  options?: BuildTreeOptions,
): Comment => {
  const voteCounts = resolveVoteCounts(input.likes, input.dislikes);
  const userVote = resolveUserVote(input.userVote);

  // Get username from backend response (authorUsername) or from author object
  const username =
    input.authorUsername || input.author?.username || "Deleted User";
  const authorId = input.authorId || input.author?.id || "";
  const avatar = input.authorAvatar || input.author?.avatar;

  // Validate required fields - fallback to warnings instead of hard crashes
  if (!input.authorUsername && !input.author?.username) {
    console.warn(
      `Comment ${input.id} missing username, fallback to "Deleted User"`,
    );
  }
  if (!input.authorId && !input.author?.id) {
    console.warn(`Comment ${input.id} missing authorId`);
  }

  return {
    id: input.id,
    author: username,
    avatar: buildAvatar(username, avatar),
    time: formatRelativeTime(input.createdAt),
    votes: voteCounts.likes - voteCounts.dislikes,
    likes: voteCounts.likes,
    dislikes: voteCounts.dislikes,
    userVote,
    content: input.body,
    isOp:
      !!options?.postAuthorUsername && username === options.postAuthorUsername,
    isOwn: !!options?.currentUserId && authorId === options.currentUserId,
    children: [],
    editedAt: input.editedAt,
    replyCount: input.replyCount,
  };
};

/**
 * Shape the backend's nested comment thread into the render tree.
 *
 * The forum thread projection (`DefaultForumCommentProjection.buildCommentTree`)
 * already assembles `replies` server-side and carries `parentId` on every node,
 * so we trust that nesting directly instead of flattening and rebuilding from
 * `parentId` (which was a redundant round-trip once the backend owned the tree).
 */
export const buildCommentTree = (
  comments: ForumComment[],
  options?: BuildTreeOptions,
): Comment[] => {
  const toNode = (input: ForumComment): Comment => {
    const node = mapToComment(input, options);
    node.children = (input.replies ?? []).map(toNode);
    return node;
  };
  return comments.map(toNode);
};

const mapSolutionComment = (
  input: SolutionComment,
  options?: BuildTreeOptions,
): Comment => {
  const voteCounts = resolveVoteCounts(input.likes, input.dislikes);
  const userVote = resolveUserVote(input.userVote);

  const username =
    input.authorUsername || input.author?.username || "Deleted User";
  const authorId = input.authorId || input.author?.id || "";
  const avatar = input.authorAvatar || input.author?.avatar;

  if (!input.authorUsername && !input.author?.username) {
    console.warn(
      `Solution comment ${input.id} missing username, fallback to "Deleted User"`,
    );
  }
  if (!input.authorId && !input.author?.id) {
    console.warn(`Solution comment ${input.id} missing authorId`);
  }

  return {
    id: input.id,
    author: username,
    avatar: buildAvatar(username, avatar),
    time: formatRelativeTime(input.createdAt),
    votes: voteCounts.likes - voteCounts.dislikes,
    likes: voteCounts.likes,
    dislikes: voteCounts.dislikes,
    userVote,
    content: input.content,
    isOp:
      !!options?.postAuthorUsername && username === options.postAuthorUsername,
    isOwn: !!options?.currentUserId && authorId === options.currentUserId,
    children: [],
    editedAt: input.updatedAt,
    replyCount: input.replyCount,
  };
};

export const buildSolutionCommentTree = (
  comments: SolutionComment[],
  options?: BuildTreeOptions,
): Comment[] => {
  const nodes = new Map<string, Comment>();
  const roots: Comment[] = [];

  comments.forEach((comment) => {
    nodes.set(comment.id, mapSolutionComment(comment, options));
  });

  comments.forEach((comment) => {
    const current = nodes.get(comment.id);
    if (!current) return;

    if (comment.parentId && nodes.has(comment.parentId)) {
      const parent = nodes.get(comment.parentId)!;
      parent.children = [...(parent.children ?? []), current];
    } else {
      roots.push(current);
    }
  });

  return roots;
};

export const countComments = (tree: Comment[]): number => {
  return tree.reduce(
    (total, node) =>
      total + 1 + (node.children ? countComments(node.children) : 0),
    0,
  );
};
