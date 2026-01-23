import { LayoutGrid, Code2, Database, Terminal, Cpu } from "lucide-vue-next";

export const PROBLEM_CATEGORIES = [
  {
    title: "sidebar.problem.allProblems",
    name: "sidebar.problem.allTopics", // Keep name for pills if we want distinction, or just use title?
    // Let's rely on title for "All Problems" if we want to align.
    // But user might want "All Topics" in the pill list.
    // The user screenshot has "All Topics" in pills.
    // To avoid transformation, I should probably have the properties ready.
    value: "all",
    slug: "",
    url: "/problemset",
    icon: LayoutGrid,
  },
  {
    title: "sidebar.problem.algorithms",
    name: "sidebar.problem.algorithms",
    value: "algorithms",
    slug: "algorithms",
    url: "/problemset/algorithms",
    icon: Code2,
  },
  {
    title: "sidebar.problem.database",
    name: "sidebar.problem.database",
    value: "database",
    slug: "database",
    url: "/problemset/database",
    icon: Database,
  },
  {
    title: "sidebar.problem.shell",
    name: "sidebar.problem.shell",
    value: "shell",
    slug: "shell",
    url: "/problemset/shell",
    icon: Terminal,
  },
  {
    title: "sidebar.problem.concurrency",
    name: "sidebar.problem.concurrency",
    value: "concurrency",
    slug: "concurrency",
    url: "/problemset/concurrency",
    icon: Cpu,
  },
] as const;

// Helper to get category by value
export function getCategoryByValue(value: string) {
  return PROBLEM_CATEGORIES.find((c) => c.value === value);
}
