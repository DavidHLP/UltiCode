import type { Component } from "vue";
import {
  Flame,
  LayoutGrid,
  TrendingUp,
  Cpu,
  Globe,
  Home,
  Trophy,
  History,
  MessageSquare,
  Briefcase,
  DollarSign,
  FileText,
  HelpCircle,
  User,
  Settings,
  List,
  Bookmark,
  CheckCircle2,
  Bell,
} from "lucide-vue-next";

export interface SidebarItem {
  title: string;
  url?: string;
  icon?: Component;
  isActive?: boolean;
  isExpanded?: boolean;
  badge?: string;
  badgeVariant?: "default" | "secondary" | "destructive" | "outline";
  children?: SidebarItem[];
  action?: () => void;
}

export interface SidebarSection {
  name: string;
  items: SidebarItem[];
  collapsible?: boolean;
}

export const forumSidebarData: SidebarSection[] = [
  {
    name: "sidebar.forum.platform",
    items: [
      { title: "sidebar.forum.home", url: "/forum", icon: Home },
      {
        title: "sidebar.forum.popular",
        url: "/forum/popular",
        icon: TrendingUp,
      },
      { title: "sidebar.forum.explore", url: "/forum/explore", icon: Globe },
      { title: "sidebar.forum.allPosts", url: "/forum/all", icon: LayoutGrid },
    ],
  },
  {
    name: "sidebar.forum.mySpace",
    collapsible: true,
    items: [
      {
        title: "sidebar.forum.myPosts",
        url: "/personal/forum-posts",
        icon: MessageSquare,
      },
      {
        title: "sidebar.forum.savedPosts",
        url: "/personal/bookmarks",
        icon: Bookmark,
      },
    ],
  },
  {
    name: "sidebar.forum.categories",
    collapsible: true,
    items: [
      {
        title: "sidebar.forum.interviewExperience",
        url: "/forum/c/interview",
        icon: MessageSquare,
      },
      {
        title: "sidebar.forum.career",
        url: "/forum/c/career",
        icon: Briefcase,
      },
      {
        title: "sidebar.forum.compensation",
        url: "/forum/c/compensation",
        icon: DollarSign,
      },
      {
        title: "sidebar.forum.technology",
        url: "/forum/c/technology",
        icon: Cpu,
      },
    ],
  },
  {
    name: "sidebar.forum.resources",
    collapsible: true,
    items: [
      {
        title: "sidebar.forum.guidelines",
        url: "/forum/guidelines",
        icon: FileText,
      },
      {
        title: "sidebar.forum.feedback",
        url: "/forum/feedback",
        icon: HelpCircle,
      },
    ],
  },
];

import { PROBLEM_CATEGORIES } from "@/constants/problem-categories";

export const problemSidebarData: SidebarSection[] = [
  {
    name: "sidebar.problem.problemSet",
    items: [...PROBLEM_CATEGORIES] as unknown as SidebarItem[],
  },
];

export const contestSidebarData: SidebarSection[] = [
  {
    name: "sidebar.contest.contestSection",
    items: [
      { title: "sidebar.contest.contestHome", url: "/contest", icon: Home },
      {
        title: "sidebar.contest.pastContests",
        url: "/contest/past",
        icon: History,
      },
      { title: "sidebar.contest.myContests", url: "/contest/my", icon: Trophy },
    ],
  },
  {
    name: "sidebar.contest.ranking",
    collapsible: true,
    items: [
      {
        title: "sidebar.contest.globalRanking",
        url: "/contest/global-ranking",
        icon: Globe,
      },
      {
        title: "sidebar.contest.localRanking",
        url: "/contest/local-ranking",
        icon: Flame,
      },
    ],
  },
];

export const personalSidebarData: SidebarSection[] = [
  {
    name: "sidebar.personal.account",
    items: [
      { title: "sidebar.personal.profile", url: "/personal", icon: User },
      {
        title: "sidebar.personal.accountSettings",
        url: "/personal/account",
        icon: Settings,
      },
      {
        title: "sidebar.personal.notifications",
        url: "/personal/notifications",
        icon: Bell,
      },
    ],
  },
  {
    name: "sidebar.personal.activity",
    items: [
      {
        title: "sidebar.personal.submissions",
        url: "/personal/submissions",
        icon: History,
      },
      {
        title: "sidebar.personal.solutions",
        url: "/personal/solutions",
        icon: CheckCircle2,
      },
      {
        title: "sidebar.personal.problemLists",
        url: "/personal/problem-lists",
        icon: List,
      },
      {
        title: "sidebar.personal.forumPosts",
        url: "/personal/forum-posts",
        icon: MessageSquare,
      },
      {
        title: "sidebar.personal.bookmarks",
        url: "/personal/bookmarks",
        icon: Bookmark,
      },
    ],
  },
];

export const sidebarData = forumSidebarData; // Default fallback
