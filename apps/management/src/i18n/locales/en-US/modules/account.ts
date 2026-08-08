export default {
  title: 'Account Settings',
  subtitle: 'Manage your profile and preferences',

  sections: {
    basic: 'Basic Information',
    about: 'About',
    social: 'Social Links',
    preferences: 'Preferences',
    security: 'Security',
    accountInfo: 'Account Information',
  },

  fields: {
    name: 'Name',
    email: 'Email',
    avatar: 'Avatar URL',
    company: 'Company',
    location: 'Location',
    bio: 'Bio',
    github: 'GitHub',
    twitter: 'Twitter',
    website: 'Website',
    preferredLanguage: 'Preferred Language',
    currentPassword: 'Current Password',
    newPassword: 'New Password',
    confirmPassword: 'Confirm Password',
    role: 'Role',
    joinedAt: 'Joined At',
    lastLogin: 'Last Login',
    username: 'Username',
  },

  actions: {
    save: 'Save Changes',
    cancel: 'Cancel',
    changePassword: 'Change Password',
  },

  toast: {
    saveSuccess: 'Profile updated successfully',
    saveFailed: 'Failed to save profile',
    passwordSuccess: 'Password updated successfully',
    passwordFailed: 'Failed to update password',
    passwordsDoNotMatch: 'Passwords do not match',
  },
} as const
