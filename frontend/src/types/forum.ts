export interface ForumUser {
  id: string;
  username: string;
  avatar?: string;
  karma?: number;
}

export interface ForumCommunityRule {
  id: string;
  title: string;
  body: string;
  sortOrder: number;
}

export interface ForumCommunityLink {
  id: string;
  label: string;
  url: string;
  description?: string;
  sortOrder: number;
}

export interface ForumTag {
  id: string;
  name: string;
  slug: string;
  description?: string;
  color?: string;
  usageCount: number;
}

export interface ForumCommunity {
  id: string;
  name: string;
  slug: string;
  description: string;

  // Visual Customization
  icon?: string;
  color?: string;
  banner?: string;

  // Stats
  members: number;
  online: number;
  postsCount?: number;
  postsToday?: number;
  postsWeek?: number;

  // Metadata
  isOfficial?: boolean;
  isFeatured?: boolean;
  sortOrder?: number;
  visibility?: "PUBLIC" | "RESTRICTED" | "PRIVATE";
  createdAt?: string;

  // Relations (populated when fetching single community)
  rules?: ForumCommunityRule[];
  links?: ForumCommunityLink[];

  // User-specific (when user is logged in)
  isMember?: boolean;
  userRole?: "OWNER" | "MODERATOR" | "MEMBER";
}

export type ForumFlairType =
  | "discussion"
  | "question"
  | "announcement"
  | "showcase"
  | "hiring";

export interface ForumFlair {
  type: ForumFlairType;
  text?: string;
}

export interface ForumAward {
  id: string;
  icon: string;
  label: string;
  count: number;
}

export interface ForumMediaBase {
  id?: string;
}

export interface ForumMediaImage extends ForumMediaBase {
  type: "image";
  kind?: "image";
  url: string;
  src?: string; // alias for url
  alt?: string;
  ratio?: number;
  caption?: string;
  thumbnail?: string;
}

export interface ForumMediaVideo extends ForumMediaBase {
  type: "video";
  kind?: "video";
  url: string;
  src?: string;
  thumbnail?: string;
  controls?: boolean;
  autoplay?: boolean;
  duration?: string;
  ratio?: number;
}

export interface ForumMediaLink extends ForumMediaBase {
  type: "link";
  kind?: "link";
  url: string;
  title?: string;
  domain?: string;
  thumbnail?: string;
  description?: string;
}

export interface ForumMediaPollOption {
  id: string;
  label: string;
  votes: number;
}

export interface ForumMediaPoll extends ForumMediaBase {
  type: "poll";
  kind?: "poll";
  question: string;
  options: ForumMediaPollOption[];
  totalVotes: number;
  closesAt?: string;
}

export interface ForumMediaText extends ForumMediaBase {
  type: "text";
  kind?: "text";
  markdown: string;
  body?: string;
}

export type ForumPostMedia =
  | ForumMediaImage
  | ForumMediaVideo
  | ForumMediaLink
  | ForumMediaPoll
  | ForumMediaText;

export interface ForumPost {
  id: string;
  title: string;
  author: ForumUser;
  createdAt: string;
  // Stats object from backend
  stats?: {
    views?: number;
    likes?: number;
    dislikes?: number;
    comments?: number;
    score?: number;
    saves?: number;
    shares?: number;
    upvote_ratio?: number;
    awards?: number;
  };
  // Frontend specific or extended properties
  likes?: number; // Direct properties
  dislikes?: number;
  userVote?: 0 | 1 | -1;

  excerpt?: string;
  tags?: string[];
  community?: ForumCommunity;
  flair?: ForumFlair;
  isPinned?: boolean;
  isLocked?: boolean;
  media?: ForumPostMedia[];
  isSaved?: boolean;
  impressions?: number;
  voteState?: "upvoted" | "downvoted" | "neutral";
  recommendation?: { label?: string };
  awards?: ForumAward[];
}

export interface ForumComment {
  id: string;
  body: string;
  author: ForumUser;
  createdAt: string;
  upvotes: number;
  likes?: number;
  dislikes?: number;
  userVote?: 0 | 1 | -1;
  replies?: ForumComment[];
  isPinned?: boolean;
  isLocked?: boolean;
  parentId?: string;
}

export interface ForumThread extends ForumPost {
  comments: ForumComment[];
  // Thread specific body/content if main post has it (backend uses excerpt/media, but if body exists add here)
  body?: string;
}
