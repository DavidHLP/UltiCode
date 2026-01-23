export default {
  // Forum sidebar
  forum: {
    platform: "Platform",
    home: "Home",
    popular: "Popular",
    explore: "Explore",
    allPosts: "All Posts",
    mySpace: "My Space",
    myPosts: "My Posts",
    savedPosts: "Saved Posts",
    categories: "Categories",
    interviewExperience: "Interview Experience",
    career: "Career",
    compensation: "Compensation",
    technology: "Technology",
    resources: "Resources",
    guidelines: "Guidelines",
    feedback: "Feedback",
  },

  // Problem sidebar
  problem: {
    problemSet: "Problem Set",
    allProblems: "All Problems",
    allTopics: "All Topics",
    algorithms: "Algorithms",
    database: "Database",
    shell: "Shell",
    concurrency: "Concurrency",
  },

  // Contest sidebar
  contest: {
    contestSection: "Contest",
    contestHome: "Contest Home",
    pastContests: "Past Contests",
    myContests: "My Contests",
    ranking: "Ranking",
    globalRanking: "Global Ranking",
    localRanking: "Local Ranking",
  },

  // Personal sidebar
  personal: {
    account: "Account",
    profile: "Profile",
    accountSettings: "Account Settings",
    activity: "Activity",
    submissions: "Submissions",
    solutions: "Solutions",
    problemLists: "Problem Lists",
    forumPosts: "Forum Posts",
    bookmarks: "Bookmarks",
    notifications: "Notifications",
    logout: "Log out",
    premiumMember: "Premium",
  },

  // User menu
  userMenu: {
    profile: "Profile",
    settings: "Settings",
    help: "Help",
    logout: "Log out",
  },

  // Problem lists in sidebar
  problemLists: {
    newList: "New List",
    newCategory: "New Category",
    myLists: "My Lists",
    savedLists: "Saved Lists",
    featured: "Featured",
    createCategory: "Create New Category",
    renameCategory: "Rename Category",
    deleteCategory: "Delete Category",
    categoryName: "Category Name",
    categoryNamePlaceholder: "e.g. Interview Prep",
    deleteCategoryConfirm:
      'Are you sure you want to delete "{name}"? Lists in this category will be moved back to "Saved Lists".',
  },
} as const;
