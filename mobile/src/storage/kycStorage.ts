import * as SecureStore from 'expo-secure-store';

const KYC_SUBMISSION_KEY = 'stash_kyc_submission';
const KYC_APPROVAL_ACKNOWLEDGED_KEY = 'stash_kyc_approval_acknowledged';
const KYC_UNDER_REVIEW_BANNER_KEY = 'stash_kyc_under_review_banner';

export interface StoredKycSubmission {
  submissionId: string;
  ownerUserId?: string;
  uploadUrls: {
    FRONT_OF_CARD: string;
    BACK_OF_CARD: string;
    SELFIE: string;
  };
  uploadedTypes: Array<'FRONT_OF_CARD' | 'BACK_OF_CARD' | 'SELFIE'>;
}

export async function saveKycSubmission(submission: StoredKycSubmission): Promise<void> {
  await SecureStore.setItemAsync(KYC_SUBMISSION_KEY, JSON.stringify(submission));
}

export async function loadKycSubmission(): Promise<StoredKycSubmission | null> {
  try {
    const raw = await SecureStore.getItemAsync(KYC_SUBMISSION_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (err) {
    console.error(err);
    return null;
  }
}

export async function clearKycSubmission(): Promise<void> {
  await SecureStore.deleteItemAsync(KYC_SUBMISSION_KEY);
}

export async function hasAcknowledgedKycApproval(): Promise<boolean> {
  try {
    const raw = await SecureStore.getItemAsync(KYC_APPROVAL_ACKNOWLEDGED_KEY);
    return raw === 'true';
  } catch (err) {
    console.error(err);
    return false;
  }
}

export async function markKycApprovalAcknowledged(): Promise<void> {
  await SecureStore.setItemAsync(KYC_APPROVAL_ACKNOWLEDGED_KEY, 'true');
}

/**
 * Set whenever the app determines the signed-in user's KYC is under review
 * (SUBMITTED/REVIEWING), cleared the moment it resolves (approved or
 * rejected). Survives logout/session-expiry/reinstall so LoginScreen can
 * show an "under review" banner even when there's no active session to
 * check the real status against.
 */
export async function markKycUnderReviewBannerPending(): Promise<void> {
  await SecureStore.setItemAsync(KYC_UNDER_REVIEW_BANNER_KEY, 'true');
}

export async function isKycUnderReviewBannerPending(): Promise<boolean> {
  try {
    const raw = await SecureStore.getItemAsync(KYC_UNDER_REVIEW_BANNER_KEY);
    return raw === 'true';
  } catch (err) {
    console.error(err);
    return false;
  }
}

export async function clearKycUnderReviewBannerPending(): Promise<void> {
  await SecureStore.deleteItemAsync(KYC_UNDER_REVIEW_BANNER_KEY);
}
