import common from './modules/common'
import nav from './modules/nav'
import users from './modules/users'
import problems from './modules/problems'
import contests from './modules/contests'
import dashboard from './modules/dashboard'
import auth from './modules/auth'
import errors from './modules/errors'
import moderation from './modules/moderation'
import settings from './modules/settings'
import table from './modules/table'
import solutions from './modules/solutions'
import forum from './modules/forum'
import problemLists from './modules/problemLists'
import audit from './modules/audit'
import account from './modules/account'
import help from './modules/help'
import analytics from './modules/analytics'
import comments from './modules/comments'
import notifications from './modules/notifications'
import submissions from './modules/submissions'
import tags from './modules/tags'
import system from './modules/system'
import scoringRules from './modules/scoring-rules'
import auditReport from './modules/audit-report'
import testCases from './modules/testCases'

export default {
  common,
  nav,
  users,
  problems,
  contests,
  dashboard,
  auth,
  errors,
  moderation,
  settings,
  table,
  solutions,
  forum,
  problemLists,
  audit,
  account,
  help,
  analytics,
  comments,
  notifications,
  submissions,
  tags,
  system,
  scoringRules,
  auditReport,
  testCases,
} as const
