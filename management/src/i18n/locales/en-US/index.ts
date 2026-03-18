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
} as const
