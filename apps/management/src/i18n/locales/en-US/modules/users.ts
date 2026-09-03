export default {
  title: 'User Management',
  addUser: 'Add User',
  createUser: 'Create User',
  createDescription: 'Fill in the information below to create a new user account',
  searchPlaceholder: 'Search by username or email...',
  banReasonPrompt: 'Please enter a reason for banning',
  editUser: 'Edit User',
  editDescription: 'Edit user information and update permission settings.',

  // Column definitions
  columns: {
    user: 'User',
    role: 'Role',
    status: 'Status',
    joined: 'Joined',
    lastLogin: 'Last Login',
    bannedUntil: 'Banned Until',
    username: 'Username',
  },

  // Filters
  filters: {
    allRoles: 'All Roles',
    allStatus: 'All Status',
    role: {
      USER: 'User',
      MODERATOR: 'Moderator',
      ADMIN: 'Admin',
      SUPER_ADMIN: 'Super Admin',
    },
    status: {
      active: 'Active',
      inactive: 'Inactive',
      banned: 'Banned',
    },
  },

  // Status Badge
  status: {
    active: 'Active',
    inactive: 'Inactive',
    banned: 'Banned',
  },

  // Form fields
  form: {
    sections: {
      general: 'General Information',
      accessControl: 'Access Control',
      securityAccess: 'Security & Access',
    },
    username: 'Username',
    usernamePlaceholder: 'johndoe',
    email: 'Email',
    emailPlaceholder: "user{'@'}example.com",
    password: 'Password',
    passwordPlaceholder: '••••••••',
    confirmPassword: 'Confirm Password',
    role: 'Role',
    isActive: 'Active Status',
    banReason: 'Ban Reason',
    banReasonPlaceholder: 'Enter ban reason...',
    name: 'Name',
    namePlaceholder: 'John Doe',
    fullName: 'Full Name',
    fullNamePlaceholder: 'John Doe',
    bio: 'Bio',
    bioPlaceholder: 'A brief introduction...',
    status: 'Status',
    creating: 'Creating...',
    createUser: 'Create User',
    saveChanges: 'Save Changes',
    saving: 'Saving...',
    noReasonProvided: 'No reason provided',
    unknown: 'Unknown',
    newPassword: 'New Password',
    newPasswordPlaceholder: 'Enter a new password',
    targetUser: 'Target User',
  },

  // Stats
  stats: {
    solved: 'Solved',
    streak: 'Streak',
    never: 'Never',
    solutions: 'Solutions',
    submissions: 'Submissions',
    accepted: 'Accepted',
    acceptanceRate: 'Acceptance Rate',
    userManagement: 'user management',
    total: 'Total',
    active: 'Active',
    banned: 'Banned',
  },

  // Actions
  actions: {
    viewDetails: 'View Details',
    editProfile: 'Edit Profile',
    resetPassword: 'Reset Password',
    banUser: 'Ban User',
    bulkBanUser: 'Bulk Ban Users',
    unbanUser: 'Unban User',
    unbanUserDescription: 'Are you sure you want to unban {username}?',
    banUserDescription: 'Please provide a reason for banning {username}.',
    thisUser: 'this user',
    resetPasswordDescription: 'Set a new login password for {username}.',
    resetPasswordWarning: 'The new password takes effect immediately. Notify the user securely.',
    cancel: 'Cancel',
    resetting: 'Resetting...',
    resetPasswordAction: 'Confirm Reset Password',
    confirmBan: 'Confirm Ban',
    confirmUnban: 'Confirm Unban',
    deleteUsers: 'Delete Users',
  },

  // Bulk actions
  bulkActions: {
    bulkBan: 'Bulk Ban',
    bulkUnban: 'Bulk Unban',
    bulkDelete: 'Bulk Delete',
  },

  // Toast messages
  toast: {
    createSuccess: 'User created successfully',
    createFailed: 'Failed to create user',
    updateSuccess: 'User updated successfully',
    updateFailed: 'Failed to update user',
    deleteSuccess: 'User deleted successfully',
    deleteFailed: 'Failed to delete user',
    banSuccess: 'User banned successfully',
    banFailed: 'Failed to ban user',
    unbanSuccess: 'User unbanned successfully',
    unbanFailed: 'Failed to unban user',
    resetPasswordSuccess: 'Password reset successfully',
    resetPasswordFailed: 'Failed to reset password',
    resetPasswordFailedDescription: 'An error occurred while attempting to update the password.',
    resetPasswordValidationFailed: 'Password too short',
    resetPasswordValidationFailedDescription: 'Password must be at least 8 characters long.',
    bulkBanFailed: 'Failed to bulk ban users',
    bulkUnbanFailed: 'Failed to bulk unban users',
    bulkDeleteFailed: 'Failed to bulk delete users',
  },

  // Dialogs
  dialogs: {
    createTitle: 'Create New User',
    editTitle: 'Edit User',
    deleteTitle: 'Confirm Delete',
    deleteDescription:
      'Are you sure you want to delete user "{username}"? This action cannot be undone.',
    resetPasswordTitle: 'Reset Password',
    resetPasswordDescription: 'Set a new password for {username}.',
  },

  // Details
  details: {
    profile: 'Profile',
    activity: 'Activity',
    submissions: 'Submissions',
    statistics: 'Statistics',
    title: 'User Details',
    description: 'View comprehensive information about the user',
    notFound: 'User not found',
  },

  drawer: {
    sections: {
      profile: 'User Profile',
      performance: 'Performance Stats',
      account: 'Account Info',
      permissions: 'Roles & Permissions',
    },
  },

  degradation: {
    partial: 'Some user data is temporarily unavailable',
    unavailable: 'User data providers are unavailable',
    profile: 'Profile data is unavailable',
    stats: 'Statistics are unavailable',
    permissions: 'Permission data is unavailable',
    noPermissions: 'No confirmed permissions for this user',
    permissionWriteDisabled: 'Permission data is unverified; role and permission writes are disabled.',
  },

  clearSelection: 'Clear Selection',
  deleteConfirm: 'Are you sure you want to delete {count} users?',
  typeToConfirm: 'Type {text} to confirm',
  typeConfirmLabel: 'To confirm, type the text below:',
} as const
