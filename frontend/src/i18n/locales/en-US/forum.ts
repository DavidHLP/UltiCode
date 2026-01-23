export default {
  // Forum list
  list: {
    title: "Forum",
    newPost: "New Post",
    trending: "Trending",
    latest: "Latest",
    noPosts: "No posts yet",
    loadMore: "Load More",
    searchPlaceholder: "Search posts, tags, or keywords...",
    sort: "Sort",
  },

  // Post
  post: {
    title: "Title",
    titlePlaceholder: "Post Title",
    content: "Content",
    contentPlaceholder: "Share your thoughts...",
    category: "Category",
    selectCategory: "Select category",
    community: "Community",
    communityPlaceholder: "Select Community",
    tags: "Tags",
    addTags: "Add tags",
    publish: "Publish",
    publishButton: "Publish",
    updateButton: "Update",
    saveDraft: "Save Draft",
    preview: "Preview",
    edit: "Edit",
    editPost: "Edit Post",
    createPost: "Create Post",
    delete: "Delete",
    share: "Share",
    report: "Report",
    pin: "Pin",
    pinned: "Pinned",
    unpin: "Unpin",
    lock: "Lock",
    locked: "Locked",
    unlock: "Unlock",
    customLabel: "Custom Label",
    selectTags: "Select Post Tags",
    editorMarkdown: "Editor (Markdown)",
    livePreview: "Live Preview",
    untitled: "Untitled Post",
    flair: "Flair",
    flairPlaceholder: "Select Flair",
    postedBy: "Posted by",
    deleteDialog: {
      title: "Delete this post?",
      description: "This action cannot be undone.",
      confirm: "Delete",
      cancel: "Cancel",
    },
    poll: {
      title: "Community poll",
      votes: "votes",
      closes: "closes",
    },
  },

  // Flairs
  flairs: {
    discussion: "Discussion",
    question: "Question",
    announcement: "Announcement",
    showcase: "Showcase",
    hiring: "Hiring",
  },

  // Comments
  comments: {
    title: "Comments",
    count: "{count} comments",
    noComments: "No comments yet",
    placeholder: "What are your thoughts?",
    reply: "Reply",
    replyTo: "Reply to @{username}",
    edit: "Edit",
    delete: "Delete",
    loadMore: "Load more comments",
    loadReplies: "Load {count} replies",
    submit: "Comment",
    collapsed: "(collapsed)",
    joinConversation: "Join the conversation...",
    threadLocked: "Thread is locked",
    beFirst: "Be the first to share what you think and start the discussion!",
    silence: "Silence is golden",
    noCommentsDesc:
      "There are no comments here yet. Why not be the first to start the conversation?",
    loading: "Loading thread...",
    failedToLoad: "Failed to load thread.",
    deleteDialog: {
      title: "Delete Comment",
      description: "Are you absolutely sure? This action cannot be undone.",
      confirm: "Delete Permanently",
      cancel: "Cancel",
    },
  },

  // Sidebar
  sidebar: {
    aboutCommunity: "About Community",
    aboutCommunityPrefix: "About",
    welcome:
      "Welcome to the forum! Browse all posts or select a category from the sidebar.",
    members: "Members",
    online: "Online",
    postsToday: "Posts Today",
    postsWeek: "Posts This Week",
    communityRules: "Community Rules",
    resources: "Resources",
  },

  // Categories
  categories: {
    all: "All",
    interviewExperience: "Interview Experience",
    interviewExperienceDesc:
      "Share and discuss technical interview experiences",
    career: "Career",
    careerDesc: "Career planning, job hunting tips, and industry insights",
    compensation: "Compensation",
    compensationDesc:
      "Salary discussions, negotiation tips, and benefits comparison",
    technology: "Technology",
    technologyDesc: "Programming languages, frameworks, and technology trends",
    general: "General",
    generalDesc: "Other discussions",
  },

  // Sorting
  sort: {
    hot: "Hot",
    new: "New",
    top: "Top",
    controversial: "Controversial",
  },

  // Actions
  actions: {
    upvote: "Upvote",
    downvote: "Downvote",
    save: "Save",
    unsave: "Unsave",
    share: "Share",
    copyLink: "Copy Link",
    report: "Report",
    back: "Back",
  },

  // Feedback
  feedback: {
    title: "Feedback & Support",
    description:
      "Help us improve UltiCode by sharing your feedback, reporting bugs, or suggesting new features.",
    submitTitle: "Submit Feedback",
    type: "Feedback Type *",
    typePlaceholder: "Select feedback type",
    subject: "Subject *",
    subjectPlaceholder: "Brief description of your feedback",
    content: "Description *",
    contentPlaceholder: "Please provide as much detail as possible...",
    bugHint: "For bug reports, please include steps to reproduce the issue.",
    submitting: "Submitting...",
    submitButton: "Submit Feedback",
    quickLinks: "Quick Links",
    communityGuidelines: "Community Guidelines",
    readRules: "Read our community rules",
    forumHome: "Forum Home",
    backToDiscussions: "Back to discussions",
    commonIssues: "Common Issues",
    issue1Title: "Can't submit code?",
    issue1Desc: "Check your code syntax and try refreshing the page.",
    issue2Title: "Login problems?",
    issue2Desc: "Try clearing your browser cache or resetting your password.",
    issue3Title: "Missing features?",
    issue3Desc: "Submit a feature request using the form!",
    types: {
      bug: "Bug Report",
      feature: "Feature Request",
      improvement: "Improvement Suggestion",
      content: "Content Issue",
      other: "Other",
    },
    success: "Feedback submitted. Thank you!",
    error: "Failed to submit feedback. Please try again.",
    fillRequired: "Please fill in all required fields.",
  },

  // Guidelines
  guidelines: {
    title: "Community Guidelines",
    subtitle:
      "Please read and follow these guidelines to keep our community welcoming and productive.",
    rules: {
      r1: {
        title: "1. Be Respectful and Professional",
        content:
          "Treat all community members with respect. Personal attacks, harassment, discrimination, or hateful language will not be tolerated. Remember that behind every username is a real person who deserves courtesy and consideration.",
      },
      r2: {
        title: "2. Stay On Topic",
        content:
          "Keep discussions relevant to the category or community you're posting in. Interview experiences belong in Interview, career advice in Career, etc. Off-topic posts may be moved or removed to maintain organization and discoverability.",
      },
      r3: {
        title: "3. No Spam or Self-Promotion",
        content:
          "Don't post repetitive content, advertisements, or excessive self-promotion. Share your projects or content when relevant, but don't spam the community. Affiliate links and commercial solicitation are not allowed.",
      },
      r4: {
        title: "4. Protect Privacy",
        content:
          "Don't share personal information (yours or others') such as phone numbers, addresses, or private messages without consent. When discussing interviews or companies, you may share your experiences but avoid naming specific interviewers or disclosing confidential information.",
      },
      r5: {
        title: "5. Use Appropriate Language",
        content:
          "Keep language professional and appropriate. Excessive profanity, NSFW content, or inappropriate jokes are not allowed. Use clear, constructive communication in all interactions.",
      },
      r6: {
        title: "6. Give Credit Where Due",
        content:
          "When sharing code, solutions, or ideas from others, always provide proper attribution. Plagiarism is not acceptable. If you're looking for someone else's work, acknowledge it and explain your contributions.",
      },
      r7: {
        title: "7. Report Issues Appropriately",
        content:
          "If you see content that violates these guidelines, use the report feature rather than engaging in arguments. The moderation team will review all reports and take appropriate action.",
      },
      r8: {
        title: "8. Constructive Feedback Only",
        content:
          "When providing feedback on code or solutions, be constructive and helpful. Focus on the work, not the person. If you disagree with someone, explain your reasoning respectfully and be open to discussion.",
      },
      enforcement: {
        title: "Enforcement",
        content:
          "Violations of these guidelines may result in warnings, temporary suspensions, or permanent bans depending on the severity and frequency. The moderation team has final discretion on all enforcement decisions.",
      },
    },
    questionsTitle: "Questions about these guidelines?",
    questionsDesc:
      "If you have questions or concerns about these guidelines, please visit our Feedback page or contact the moderation team.",
  },

  // Messages
  messages: {
    postCreated: "Post published successfully",
    postUpdated: "Post updated successfully",
    postDeleted: "Post deleted",
    commentAdded: "Comment added",
    commentDeleted: "Comment deleted",
    saved: "Post saved",
    unsaved: "Post unsaved",
    linkCopied: "Link copied to clipboard",
    copyFailed: "Failed to copy link",
    reportSubmitted: "Report submitted",
    postNotFound: "Post not found.",
    loginToPublish: "Please log in to publish.",
    loginToComment: "Please log in to comment.",
    loginToVote: "Please log in to vote.",
    loginToEdit: "Please log in to edit comments.",
    loginToDelete: "Please log in to delete comments.",
    loginToSave: "Please log in to save posts.",
    loginToCreate: "Please log in to create a post.",
    fillRequired: "Please fill in the title, content, and community.",
    saveFailed: "Failed to save post.",
    loadFailed: "Failed to load editor data",
    voteFailed: "Failed to vote.",
    commentEditFailed: "Failed to edit comment.",
    commentDeleteFailed: "Failed to delete comment.",
    deleteFailed: "Failed to delete post.",
  },
} as const;
