# Phase 29: Social Frontend - Discussion Log (Assumptions Mode)

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the analysis.

**Date:** 2026-04-21
**Phase:** 29-social-frontend
**Mode:** assumptions
**Areas analyzed:** Route & Page Structure, Follow Button Component, Follow API Client, Achievements on Profile, Profile Data Composition, i18n

## Assumptions Presented

### Route & Page Structure
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| New `/profile/:username` route for social profile page | Confident | REQUIREMENTS.md specifies `/profile/{username}`; existing `/users/:id` uses numeric ID |
| Existing `UserProfileView.vue` remains unchanged | Confident | Different layout/purpose; `UserProfileView` is stats-focused, new page is social-focused |
| Use `AppLayout.vue` sidebar layout | Likely | All authenticated pages use this pattern |

### Follow Button Component
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Isolated `FollowButton.vue` component | Confident | Standard component isolation pattern in codebase |
| "Follow" → "Following" with hover-to-unfollow pattern | Likely | Standard UX pattern (Twitter/GitHub); REQUIREMENTS.md specifies this behavior |
| Hidden on own profile | Confident | Success criteria: "visible only when viewing other users' profiles" |
| Optimistic UI update with rollback | Likely | Best practice for responsive follow actions; existing interaction.ts uses edge-operations for similar pattern |

### Follow API Client
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| New `follow.ts` API client | Confident | Follow endpoints exist in backend; no frontend client yet (grep confirmed) |
| Need new `GET /users/{id}/follow/status` endpoint | Likely | No existing way to check if current user follows a specific user without fetching full follower list |

### Achievements on Profile
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Reuse `AchievementCard.vue` and `AchievementBadge.vue` | Confident | Components are feature-complete with tier, progress, category support |
| Show top 5 earned with "View all" link | Likely | Standard profile pattern; avoids page heavy load |
| Need `GET /users/{id}/achievements` endpoint | Likely | Current `GET /achievements/my` only returns current user's achievements; viewing another user's achievements needs a separate endpoint |

### Profile Data Composition
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Use existing `GET /users/{id}/profile` (ProfileVO) | Confident | Phase 27 ProfileVO includes all needed fields: followerCount, followingCount, achievementCount |
| Follow button state fetched separately | Likely | Decouples profile loading from auth-dependent follow status |

### i18n
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Add strings to existing `personal` locale namespace | Likely | Profile-related strings naturally belong alongside existing personal/profile strings |

## Corrections Made

No corrections — all assumptions auto-confirmed in `--auto` mode.

## Auto-Resolved

All assumptions were Confident or Likely — no Unclear items required auto-resolution.

## External Research

No external research needed — all decisions grounded in existing codebase patterns and prior phase decisions.