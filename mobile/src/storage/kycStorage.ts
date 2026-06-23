import * as SecureStore from 'expo-secure-store';

const KYC_SUBMISSION_KEY = 'stash_kyc_submission';

export interface StoredKycSubmission {
  submissionId: string;
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
  } catch {
    return null;
  }
}

export async function clearKycSubmission(): Promise<void> {
  await SecureStore.deleteItemAsync(KYC_SUBMISSION_KEY);
}
