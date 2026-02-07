import type { Comment } from "@/types/comment";
import type { ForumComment } from "@/types/forum";
import { formatRelativeTime } from "@/utils/date";
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

  return {
    id: input.id,
    author: input.author.username,
    avatar: buildAvatar(input.author.username, input.author.avatar),
    time: formatRelativeTime(input.createdAt),
    votes: voteCounts.likes - voteCounts.dislikes,
    likes: voteCounts.likes,
    dislikes: voteCounts.dislikes,
    userVote,
    content: input.body,
    isOp:
      !!options?.postAuthorUsername &&
      input.author.username === options.postAuthorUsername,
    isOwn:
      !!options?.currentUserId && input.author.id === options.currentUserId,
    children: [],
  };
};

// Normalizes flat lists and nested replies into a consistent tree structure
export const buildCommentTree = (
  comments: ForumComment[],
  options?: BuildTreeOptions,
): Comment[] => {
  const nodes = new Map<string, Comment>();
  const roots: Comment[] = [];

  const flatten = (item: ForumComment, parentId?: string): ForumComment[] => {
    const current: ForumComment = {
      ...item,
      parentId: item.parentId ?? parentId,
    };
    const nested =
      item.replies?.flatMap((reply) => flatten(reply, item.id)) ?? [];
    return [current, ...nested];
  };

  const normalized = comments.flatMap((comment) => flatten(comment));

  normalized.forEach((comment) => {
    nodes.set(comment.id, mapToComment(comment, options));
  });

  normalized.forEach((comment) => {
    const current = nodes.get(comment.id);
    if (!current) return;

    if (comment.parentId && nodes.has(comment.parentId)) {
      const parent = nodes.get(comment.parentId)!;
      parent.children = parent.children ?? [];
      parent.children.push(current);
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
