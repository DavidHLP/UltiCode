export default {
  // Achievement gallery
  title: "Achievements",
  description:
    "Track your progress and earn badges as you solve problems, participate in contests, and contribute to the community.",

  // Stats
  earned: "Earned",
  total: "Total",
  points: "Points",
  complete: "Complete",

  // Categories
  categories: {
    all: "All",
    problemSolving: "Problem Solving",
    consistency: "Consistency",
    contest: "Contest",
    community: "Community",
  },

  // Empty state
  empty: {
    title: "No achievements yet",
    description: "Start solving problems to earn your first badge!",
  },

  // Badge unlock toast
  unlock: {
    title: "Achievement Unlocked!",
    earnedPoints: "You earned {points} points!",
  },
} as const;
