import * as SecureStore from 'expo-secure-store';

const KYC_SUBMISSION_KEY = 'stash_kyc_submission';
const KYC_APPROVAL_ACKNOWLEDGED_KEY = 'stash_kyc_approval_acknowledged';

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
