import { MessageSquare, Briefcase, DollarSign, Cpu } from "lucide-vue-next";
import type { FunctionalComponent, SVGAttributes } from "vue";

type LucideIcon = FunctionalComponent<SVGAttributes>;

export interface ForumCategoryMetadata {
  slug: string;
  name: string;
  icon: LucideIcon;
  color: string;
  description: string;
}

// Metadata for mapping icon names from backend to Vue components
export const FORUM_CATEGORY_METADATA: Record<string, ForumCategoryMetadata> = {
  interview: {
    slug: "interview",
    name: "Interview Experience",
    icon: MessageSquare,
    color: "#3B82F6",
    description: "Share and discuss interview experiences",
  },
  career: {
    slug: "career",
    name: "Career",
    icon: Briefcase,
    color: "#10B981",
    description: "Career advice and opportunities",
  },
  compensation: {
    slug: "compensation",
    name: "Compensation",
    icon: DollarSign,
    color: "#F59E0B",
    description: "Salary and benefits discussions",
  },
  technology: {
    slug: "technology",
    name: "Technology",
    icon: Cpu,
    color: "#8B5CF6",
    description: "Technical discussions",
  },
};

// Icon name to component mapping (for dynamic icon rendering)
export const ICON_MAP: Record<string, LucideIcon> = {
  MessageSquare,
  Briefcase,
  DollarSign,
  Cpu,
};

export function getCategoryMetadata(
  slug: string,
): ForumCategoryMetadata | null {
  return FORUM_CATEGORY_METADATA[slug] || null;
}

export function getCategoryIcon(iconName: string): LucideIcon | null {
  return ICON_MAP[iconName] || null;
}
