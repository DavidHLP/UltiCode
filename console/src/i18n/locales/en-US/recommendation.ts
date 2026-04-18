export default {
  title: "Problem Recommendations",
  description: {
    daily: "Personalized daily picks based on your practice history and proficiency",
    weakPoints: "Targeted practice for your weak knowledge areas",
    challenge: "Push beyond your comfort zone with harder problems",
    similar: "Find problems similar to a specific problem to reinforce patterns",
  },
  filter: {
    tags: "Filter by Tags",
    allTags: "All Tags",
    all: "Select All",
    refresh: "Refresh",
  },
  card: {
    score: "Score",
    reason: "Reason",
  },
  empty: {
    daily: "No daily recommendations yet. Start solving problems!",
    "weak-points":
      "No weak point data yet. Keep practicing for better recommendations.",
    challenge:
      "No challenge problems available. Complete more medium problems first.",
    similar: "Search for a problem to find similar ones.",
  },
  search: {
    placeholder: "Search problems...",
    noResults: "No problems found",
  },
} as const;
