export default {
  title: 'Subscription',
  subtitle: 'View and manage your subscription plan',

  currentPlan: 'Current Plan',
  planDetails: 'Plan Details',
  statusLabel: 'Status',
  startedAt: 'Started At',
  expiresAt: 'Expires At',
  cancelledAt: 'Cancelled At',
  noSubscription: 'You are currently on the free plan',

  status: {
    ACTIVE: 'Active',
    CANCELLED: 'Cancelled',
    EXPIRED: 'Expired',
    PENDING: 'Pending',
  },

  plans: {
    FREE: 'Free',
    PRO: 'Pro',
    PREMIUM: 'Premium',
  },

  features: {
    free: {
      title: 'Free Plan Features',
      description: 'Basic features for getting started',
    },
    premium: {
      title: 'Premium Plan Features',
      description: 'Unlock all features for the best experience',
    },
    premiumProblems: 'Access to all premium problems',
    prioritySupport: 'Priority support',
    advancedAnalytics: 'Advanced analytics and insights',
    unlimitedContests: 'Unlimited contest participation',
    freeProblems: 'Access to free problems',
    communityForum: 'Community forum access',
    basicAnalytics: 'Basic analytics',
  },

  upgradePrompt: 'Upgrade to Premium to unlock all features and get the most out of the platform.',
  manageSubscription: 'To manage your subscription, please contact support.',
} as const
