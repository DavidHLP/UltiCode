# Cross-Owner Reference Manifest (P5-RECONCILE-001)

Source of truth for cross-owner logical foreign keys that the orphan scanner checks.
Each entry defines a child table column that references a parent table owned by a different domain.

This manifest is loaded at startup by `OwnerReconciler` to drive orphan detection.
Update this file when tables move between owners or new cross-owner references are introduced.

## Auth-owned tables (parent side)

| child_table | child_column | child_owner | parent_table | parent_owner |
|-------------|-------------|-------------|-------------|-------------|
| refresh_tokens | user_id | Auth | users | Auth |
| password_resets | user_id | Auth | users | Auth |
| oauth_provider_identities | user_id | Auth | users | Auth |
| user_permissions | user_id | Auth | users | Auth |

## Admin-owned tables (parent side)

| child_table | child_column | child_owner | parent_table | parent_owner |
|-------------|-------------|-------------|-------------|-------------|
| audit_logs | performer_id | Admin | users | Auth |

## App-owned tables (cross-owner references to Auth)

| child_table | child_column | child_owner | parent_table | parent_owner |
|-------------|-------------|-------------|-------------|-------------|
| submissions | user_id | App | users | Auth |
| solutions | user_id | App | users | Auth |
| forum_posts | user_id | App | users | Auth |
| notifications | user_id | App | users | Auth |
| user_profiles | account_id | App | users | Auth |
| contest_participants | user_id | App | users | Auth |
| user_achievements | user_id | App | users | Auth |
| user_follows | follower_id | App | users | Auth |
| user_follows | following_id | App | users | Auth |

## Vertical-split reconciliation pairs (dual-write tables)

| source_table | source_owner | target_table | target_owner |
|-------------|-------------|-------------|-------------|
| users | Auth | user_profiles | App |
