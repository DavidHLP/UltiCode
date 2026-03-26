export default {
  title: 'User Management',
  addUser: 'Add User',
  createUser: 'Create User',
  createDescription: 'Fill in the information below to create a new user account',
  searchPlaceholder: 'Search by username or email...',
  banReasonPrompt: 'Please enter a reason for banning',

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

  // Form fields
  form: {
    username: 'Username',
    usernamePlaceholder: 'johndoe',
    email: 'Email',
    emailPlaceholder: 'user@example.com',
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
    noReasonProvided: 'No reason provided',
    unknown: 'Unknown',
  },

  // Stats
  stats: {
    solved: 'Solved',
    streak: 'Streak',
    never: 'Never',
  },

  // Actions
  actions: {
    viewDetails: 'View Details',
    editProfile: 'Edit Profile',
    resetPassword: 'Reset Password',
    banUser: 'Ban User',
    unbanUser: 'Unban User',
    banUserDescription: 'Please provide a reason for banning {username}.',
    thisUser: 'this user',
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

  clearSelection: 'Clear Selection',
  deleteConfirm: 'Are you sure you want to delete {count} users?',
} as const
