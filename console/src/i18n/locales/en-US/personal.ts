export default {
  // Profile
  profile: {
    title: "Profile",
    editProfile: "Edit Profile",
    avatar: "Avatar",
    changeAvatar: "Change Avatar",
    username: "Username",
    displayName: "Display Name",
    bio: "Bio",
    bioPlaceholder: "Tell us about yourself...",
    location: "Location",
    locationPlaceholder: "City, Country",
    website: "Website",
    websitePlaceholder: "https://",
    github: "GitHub",
    githubPlaceholder: "GitHub username",
    company: "Company",
    companyPlaceholder: "Company name",
    school: "School",
    schoolPlaceholder: "School name",
    joinDate: "Joined",
    lastActive: "Last Active",
    proMember: "Pro Member",
    joinedDate: "Joined {date}",
    noBio: "No bio provided. Write something about yourself!",
    proBadge: "Pro Member",
    globalRank: "Global Rank",
    solved: "Solved",
    streak: "Streak",
    solvingProgress: "Problem Solving Progress",
    heatmapTitle: "Submission Heatmap",
    heatmapSubtitle: "Your activity across the platform over the last year.",
    recentActivity: "Recent Activity",
    viewAllSubmissions: "VIEW ALL SUBMISSIONS",
    overallProgress: "OVERALL",
    totalProblems: "Total problems completed",
    authenticationRequired: "Authentication Required",
    loginToView: "Please log in to view and manage your profile.",
    signIn: "Sign In",
    loadingProfile: "Loading profile...",
    contributions: "contributions",
  },

  // Account settings
  account: {
    title: "Account Settings",
    subtitle:
      "Manage your personal information, security, and notification preferences.",
    loadingSettings: "Loading your settings...",
    loginToManage: "Please log in to manage your account.",
    tabs: {
      general: "General",
      security: "Security",
      notifications: "Notifications",
    },
    sections: {
      publicProfile: "Public Profile",
      publicProfileDesc:
        "This information will be displayed publicly on your profile page.",
      webPresence: "Web Presence",
      webPresenceDesc:
        "Add links to your professional profiles and personal website.",
      notifications: "Notification Preferences",
      notificationsDesc: "Choose how you want to be notified about activity.",
    },
    email: "Email",
    changeEmail: "Change Email",
    password: "Password",
    changePassword: "Change Password",
    currentPassword: "Current Password",
    newPassword: "New Password",
    confirmNewPassword: "Confirm New Password",
    twoFactor: "Two-Factor Authentication",
    enable2FA: "Enable 2FA",
    disable2FA: "Disable 2FA",
    connectedAccounts: "Connected Accounts",
    connectGithub: "Connect GitHub",
    disconnectGithub: "Disconnect GitHub",
    dangerZone: "Danger Zone",
    dangerZoneDesc: "Permanently delete your account and all of your content.",
    deleteWarning:
      "Once you delete your account, there is no going back. Please be certain.",
    deleteAccount: "Delete Account",
    deleteAccountWarning:
      "Once you delete your account, all data will be permanently removed and cannot be recovered.",
    exportData: "Export Data",
    usernameUnique: "Usernames are unique and cannot be changed.",
    saveChanges: "Save All Changes",
    updatePassword: "Update Password",
    notificationTypes: {
      communication: "Communication",
      communicationDesc:
        "Receive emails about your account activity and updates.",
      marketing: "Marketing",
      marketingDesc: "Receive emails about new products, features, and offers.",
      security: "Security Alerts",
      securityDesc: "Critical security notifications (cannot be disabled).",
    },
  },

  // Stats
  stats: {
    solved: "Solved",
    easy: "Easy",
    medium: "Medium",
    hard: "Hard",
    submissions: "Submissions",
    acceptanceRate: "Acceptance Rate",
    ranking: "Ranking",
    rating: "Rating",
    contestsAttended: "Contests",
    streak: "Streak",
    days: "days",
    topPercent: "Top {percent}%",
  },

  // Submissions
  submissions: {
    title: "My Submissions",
    subtitle: "A historical record of all your problem solving attempts.",
    noSubmissions: "No submissions found",
    noSubmissionsDesc:
      "You haven't submitted any code yet. Ready to take on your first challenge?",
    startSolving: "Start Solving Problems",
    browseProblems: "Browse Problems",
    loadingSubmissions: "Retrieving submission history...",
    loginToView: "Please log in to view your submissions.",
    recentAttempts: "Recent Attempts",
    latestSubmissions: "Displaying your latest {count} submissions.",
    totalSubmissions: "{count} Total",
    problem: "Problem",
    status: "Status",
    language: "Language",
    runtime: "Runtime",
    memory: "Memory",
    submittedAt: "Submitted At",
    viewCode: "View Code",
  },

  // Solutions
  solutions: {
    title: "My Solutions",
    subtitle:
      "Manage and track the performance of your shared technical solutions.",
    noSolutions: "No solutions shared yet",
    noSolutionsDesc:
      "Sharing your thought process helps you and others learn better. Post your first solution!",
    newSolution: "New Solution",
    startCoding: "Start Coding",
    loadingSolutions: "Loading your solutions...",
    loginToView: "Please log in to view your solutions.",
    views: "Views",
    votes: "Votes",
    comments: "Comments",
    details: "DETAILS",
    edit: "Edit",
    delete: "Delete",
    view: "View",
    noTags: "No tags",
  },

  // Problem lists
  problemLists: {
    title: "Problem Lists",
    subtitle: "Curate, organize, and track your practice problem sets.",
    loadingLists: "Loading your collections...",
    loginToManage:
      "Please log in to view and manage your custom problem lists.",
    tabs: {
      myLists: "My Lists",
      saved: "Saved",
      categories: "Categories",
      featured: "Featured",
    },
    actions: {
      newCategory: "New Category",
      newList: "New List",
      editList: "Edit List",
      deleteList: "Delete List",
      save: "SAVE",
      unsave: "UNSAVE",
      unsaveList: "Unsave List",
      moveToCategory: "Move to Category",
      moveToAnother: "Move to Another",
      removeFromCategory: "Remove from Category",
      renameCategory: "Rename Category",
      deleteCategory: "Delete Category",
    },
    listCard: {
      public: "Public",
      private: "Private",
      saved: "SAVED",
      featured: "FEATURED",
      noDescription: "No description provided.",
      problemCount: "{count} PROBLEMS",
      problemsCount: "{count} problems",
    },
    emptyStates: {
      noLists: "No problem lists yet",
      noListsDesc:
        "Create your first problem list to organize your practice and track your progress.",
      createFirst: "Create Your First List",
      noSaved: "No saved lists yet",
      noSavedDesc:
        "Save problem lists created by others to keep them organized here.",
      noFeatured: "No featured lists",
      noFeaturedDesc: "Check back later for curated problem lists.",
      noListsInCategory: "No lists in this category yet",
      noListsInCategoryDesc: "No lists in this category.",
    },
    categories: {
      uncategorized: "Uncategorized",
      listCount: "{count} LIST{s}",
    },
    dialogs: {
      createList: "Create New List",
      createListDesc:
        "Organize your practice with a custom problem collection.",
      listName: "List Name",
      listNamePlaceholder: "e.g. Dynamic Programming Masterclass",
      description: "Description",
      descriptionPlaceholder: "What is this collection for?",
      publicList: "Public List",
      publicListDesc: "Visible to the community",
      createButton: "Create List",
      creating: "Creating...",
      deleteList: "Delete Problem List",
      deleteListConfirm:
        'Are you sure you want to delete "{name}"? This action is permanent.',
      newCategory: "New Category",
      newCategoryDesc: "Group your saved lists to keep them organized.",
      categoryName: "Category Name",
      categoryNamePlaceholder: "e.g. Meta Prep 2026",
      createCategory: "Create Category",
      renameCategory: "Rename Category",
      newName: "New Name",
      deleteCategory: "Delete Category",
      deleteCategoryConfirm:
        'Are you sure you want to delete "{name}"? Your saved lists will be moved to the "Uncategorized" section.',
    },
  },

  // Forum posts
  forumPosts: {
    title: "My Forum Posts",
    subtitle:
      "View and manage the discussions you've started in the community.",
    loadingPosts: "Loading your posts...",
    noPosts: "No posts yet",
    noPostsDesc:
      "You haven't started any discussions. Share your knowledge or ask a question!",
    createFirst: "Create Your First Post",
    newPost: "New Post",
    pinned: "PINNED",
    locked: "LOCKED",
    viewPost: "View Post",
    edit: "Edit",
    delete: "Delete",
    deleteDialog: {
      title: "Delete this post?",
      description:
        "This action cannot be undone. This post and all of its comments will be permanently removed.",
    },
    stats: {
      comments: "Comments",
      views: "Views",
    },
  },

  // Bookmarks
  bookmarks: {
    title: "Collections",
    subtitle: "Organize your saved problems, solutions, and forum discussions.",
    folders: "My Folders",
    newCollection: "New Collection",
    searchPlaceholder: "Search items...",
    loadingItems: "Loading items...",
    noCollectionSelected: "No collection selected",
    noCollectionSelectedDesc:
      "Choose a collection from the sidebar to view your bookmarks.",
    emptyCollection: "This collection is empty",
    emptyCollectionDesc:
      "Browse the platform and save interesting problems or discussions here.",
    noResults: "No items match your search.",
    actions: {
      openItem: "Open Item",
      removeFromCollection: "Remove from Collection",
    },
    dialogs: {
      deleteTitle: "Delete Collection",
      deleteConfirm:
        'Are you sure you want to delete "{name}"? This action cannot be undone and all bookmarks inside will be lost.',
    },
  },

  // Notifications
  notifications: {
    title: "Notifications",
    noNotifications: "No notifications",
    markAllAsRead: "Mark All as Read",
    markAsRead: "Mark as Read",
    clearAll: "Clear All",
    recent: "Recent",
    emptyStateDesc: "You're all caught up! No new notifications for now.",
    types: {
      comment: "commented on your",
      reply: "replied to you",
      mention: "mentioned you",
      upvote: "upvoted your",
      follow: "followed you",
      system: "System notification",
      submission: "Submission update",
      contest: "Contest update",
    },
  },

  // Messages
  messages: {
    profileUpdated: "Profile updated",
    passwordChanged: "Password changed",
    avatarUpdated: "Avatar updated",
    emailChanged: "Email changed",
    folderCreated: "Folder created",
    folderDeleted: "Folder deleted",
    bookmarkAdded: "Added to bookmarks",
    bookmarkRemoved: "Removed from bookmarks",
    notificationsCleared: "Notifications cleared",
    notificationsMarkedRead: "All notifications marked as read",
    notificationsUpdated: "Notification preferences updated",
  },
} as const;
