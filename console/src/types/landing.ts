/**
 * Landing-page domain types.
 *
 * Keeps template-literal i18n keys (`landing.feature.${key}`) tied to a
 * compile-time union so the template cannot drift from the locale file.
 */

export type Tone = 'electric' | 'green' | 'amber' | 'purple' | 'cyan' | 'red';

export type CapabilityKey =
  | 'editor'
  | 'judge'
  | 'contest'
  | 'lists'
  | 'solutions'
  | 'community';

export type FaqKey =
  | 'free'
  | 'judge_speed'
  | 'privacy'
  | 'school'
  | 'languages'
  | 'offline'
  | 'api'
  | 'partnership';

export type UseCaseKey =
  | 'learner'
  | 'school'
  | 'enterprise'
  | 'contest'
  | 'interview';

export type TimelineKey =
  | 'editor'
  | 'judge'
  | 'contest'
  | 'lists'
  | 'solutions'
  | 'community'
  | 'auth';

export interface LandingCapability {
  key: CapabilityKey;
  tone: Tone;
}