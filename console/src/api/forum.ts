import { apiDelete, apiGet, apiPatch, apiPost } from "@/utils/request";
import type {
  ForumComment,
  ForumCommunity,
  ForumCommunityRule,
  ForumCommunityLink,
  ForumPost,
  ForumThread,
  ForumTag,
} from "@/types/forum";

export async function fetchForumPosts(): Promise<ForumPost[]> {
  const response = await apiGet<
    Array<{
      userId: string;
      authorUsername?: string;
      authorAvatar?: string;
    } & Omit<ForumPost, "author">>
  >("/forum/posts");

  // Transform flat author fields to nested author object
  return response.map((post) => ({
    ...post,
    author: {
      id: post.userId,
      username: post.authorUsername ?? post.userId,
      avatar: post.authorAvatar,
    },
  }));
}

export async function fetchForumPost(postId: string): Promise<ForumPost> {
  return apiGet<ForumPost>(`/forum/posts/${postId}`);
}

export async function fetchForumCommunities(options?: {
  featured?: boolean;
}): Promise<ForumCommunity[]> {
  const params = options?.featured ? "?featured=true" : "";
  return apiGet<ForumCommunity[]>(`/forum/communities${params}`);
}

export async function fetchForumCommunity(slugOrId: string): Promise<{
  community: ForumCommunity | null;
  rules: ForumCommunityRule[];
  links: ForumCommunityLink[];
}> {
  return apiGet(`/forum/communities/${slugOrId}`);
}

export async function fetchCommunityPosts(
  slug: string,
  options?: { sortBy?: "hot" | "new" | "top" },
): Promise<ForumPost[]> {
  const params = options?.sortBy ? `?sortBy=${options.sortBy}` : "";
  const response = await apiGet<
    Array<{
      userId: string;
      authorUsername?: string;
      authorAvatar?: string;
    } & Omit<ForumPost, "author">>
  >(`/forum/communities/${slug}/posts${params}`);

  // Transform flat author fields to nested author object
  return response.map((post) => ({
    ...post,
    author: {
      id: post.userId,
      username: post.authorUsername ?? post.userId,
      avatar: post.authorAvatar,
    },
  }));
}

export async function fetchForumTags(): Promise<ForumTag[]> {
  return apiGet<ForumTag[]>("/forum/tags");
}

export async function joinCommunity(communityId: string): Promise<void> {
  return apiPost(`/forum/communities/${communityId}/join`, {});
}

export async function leaveCommunity(communityId: string): Promise<void> {
  return apiPost(`/forum/communities/${communityId}/leave`, {});
}

export async function fetchForumQuickFilters(): Promise<
  Array<{ label: string; value: string }>
> {
  return apiGet<Array<{ label: string; value: string }>>(
    "/forum/quick-filters",
  );
}

export async function fetchForumThread(
  postId: string,
  userId?: string,
): Promise<ForumThread> {
  const url = userId
    ? `/forum/posts/${postId}/thread?userId=${userId}`
    : `/forum/posts/${postId}/thread`;

  // Transform API response from { post, comments } wrapper to flat ForumThread structure
  const response = await apiGet<{
    post: ForumThread & {
      userId: string;
      authorUsername: string;
      authorAvatar: string;
    };
    comments: ForumComment[];
  }>(url);

  const { post, comments } = response;

  // Transform flat author fields to nested author object that ForumPost expects
  return {
    ...post,
    author: {
      id: post.userId,
      username: post.authorUsername,
      avatar: post.authorAvatar,
    },
    comments,
  };
}

export async function createForumComment(
  postId: string,
  body: string,
  parentId?: string | null,
): Promise<void> {
  await apiPost<unknown>(`/forum/posts/${postId}/comments`, {
    body,
    parentId: parentId ?? null,
  });
}

export async function updateForumComment(
  commentId: string,
  body: string,
): Promise<void> {
  await apiPatch(`/forum/comments/${commentId}`, { body });
}

export async function deleteForumComment(commentId: string): Promise<void> {
  await apiDelete(`/forum/comments/${commentId}`);
}

export async function recordForumView(postId: string) {
  // Call the forum-specific view recording endpoint
  return apiPost(`/forum/posts/${postId}/view`, {});
}

export async function recordForumShare(postId: string) {
  return apiPost(`/forum/posts/${postId}/share`, {});
}

export async function createForumPost(input: {
  title: string;
  excerpt: string;
  communityId: string;
  tags?: string[];
  flairType?: string | null;
  flairLabel?: string | null;
}): Promise<ForumPost> {
  return apiPost<ForumPost>("/forum/posts", input);
}

export async function updateForumPost(
  postId: string,
  input: Partial<{
    title: string;
    excerpt: string;
    tags: string[];
    flairType: string | null;
    flairLabel: string | null;
    isPinned: boolean;
    isLocked: boolean;
  }>,
): Promise<ForumPost> {
  return apiPatch<ForumPost>(`/forum/posts/${postId}`, input);
}

export async function deleteForumPost(postId: string): Promise<void> {
  await apiDelete(`/forum/posts/${postId}`);
}

export async function fetchMyForumPosts(): Promise<ForumPost[]> {
  return apiGet<ForumPost[]>("/forum/me/posts");
}
