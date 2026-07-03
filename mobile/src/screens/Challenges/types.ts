// The backend has no read endpoint for challenges — only POST
// /api/v1/challenges/{id}/join exists (challenge/api/ChallengeController.java).
// This is the proposed contract for the missing GET /api/v1/challenges and
// GET /api/v1/challenges/{id}, field-compatible with the real
// JoinChallengeResponse DTO's camelCase (id, challengeId, status,
// targetAmount, progressAmount, enrolledAt) and the real
// challenge.savings_challenges / challenge.user_challenges / challenge.badges
// column names — see the PR notes for the full proposal.

export type ChallengeEnrollmentStatus = 'ACTIVE' | 'COMPLETED' | 'FAILED' | 'ABANDONED';

export interface ChallengeEnrollment {
  status: ChallengeEnrollmentStatus;
  progressAmount: number;
  enrolledAt: string;
  completedAt: string | null;
}

export interface Challenge {
  id: string;
  name: string;
  description: string;
  targetAmount: number | null; // null for NO_WITHDRAWAL-type challenges
  targetDurationDays: number;
  badgeCode: string;
  badgeName: string;
  badgeAssetName: string; // challenge.badges.asset_name — no real asset files exist yet, see BadgePreview
  enrollment: ChallengeEnrollment | null; // null = not enrolled
}

// Client-side partition of the single GET /api/v1/challenges response —
// there's no separate backend query per section, this is derived.
export type ChallengeSection = 'ACTIVE' | 'AVAILABLE' | 'COMPLETED';

export function sectionFor(challenge: Challenge): ChallengeSection {
  if (!challenge.enrollment) return 'AVAILABLE';
  if (challenge.enrollment.status === 'COMPLETED') return 'COMPLETED';
  if (challenge.enrollment.status === 'ACTIVE') return 'ACTIVE';
  // FAILED / ABANDONED have no producing code path yet (join only ever
  // creates ACTIVE), but if they occur, treat them as re-joinable rather
  // than inventing a fourth section this AC doesn't define.
  return 'AVAILABLE';
}
