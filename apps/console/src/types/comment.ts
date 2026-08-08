export interface Comment {
  id: string | number;
  author: string;
  avatar: string;
  time: string;
  votes: number;
  likes: number;
  dislikes: number;
  userVote?: 0 | 1 | -1;
  content: string;
  isOp?: boolean;
  isOwn?: boolean;
  children?: Comment[];
  editedAt?: string;
  replyCount?: number;
}

export interface ForumUser {
  id?: string;
  username?: string;
  avatar?: string;
}

export interface ForumComment {
  id: string;
  postId?: string;
  parentId?: string;
  authorId?: string;
  authorUsername?: string;
  authorAvatar?: string;
  body: string;
  markdown?: string;
  createdAt: string;
  editedAt?: string;
  isPinned?: boolean;
  isLocked?: boolean;
  isFlagged?: boolean;
  flaggedReason?: string;
  flaggedAt?: string;
  isAuthor?: boolean;
  replyCount?: number;
  replies?: ForumComment[];
  author?: ForumUser;
  upvotes?: number;
  likes?: number;
  dislikes?: number;
  userVote?: 0 | 1 | -1;
}

export interface SolutionComment {
  id: string;
  solutionId?: string;
  parentId?: string;
  authorId?: string;
  authorUsername?: string;
  authorAvatar?: string;
  content: string;
  createdAt: string;
  updatedAt?: string;
  isFlagged?: boolean;
  flaggedReason?: string;
  replyCount?: number;
  replies?: SolutionComment[];
  author?: ForumUser;
  upvotes?: number;
  likes?: number;
  dislikes?: number;
  userVote?: 0 | 1 | -1;
}
