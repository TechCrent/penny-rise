import { getMySubmission } from '../api/kyc';
import { loadKycSubmission } from '../storage/kycStorage';
import type { RootStackParamList } from './RootNavigator';

type PostAuthRoute = {
  [K in keyof RootStackParamList]: RootStackParamList[K] extends undefined
    ? { name: K }
    : { name: K; params: RootStackParamList[K] };
}[keyof Pick<
  RootStackParamList,
  'Home' | 'KycCardDetails' | 'KycDocumentUpload' | 'KycSubmissionPending'
>];

const ACTIVE_SUBMISSION_STATUSES = ['REVIEWING', 'SUBMITTED'];

export async function resolvePostAuthNavigation(kycStatus: string): Promise<PostAuthRoute> {
  const stored = await loadKycSubmission();
  if (stored) {
    return {
      name: 'KycDocumentUpload',
      params: {
        submissionId: stored.submissionId,
        uploadUrls: stored.uploadUrls,
      },
    };
  }

  if (kycStatus === 'APPROVED') {
    return { name: 'Home' };
  }

  if (kycStatus === 'SUBMITTED') {
    const submission = await getMySubmission();
    if (submission) {
      return { name: 'KycSubmissionPending', params: { submissionId: submission.id } };
    }
    return { name: 'Home' };
  }

  const submission = await getMySubmission();
  if (submission && ACTIVE_SUBMISSION_STATUSES.includes(submission.status)) {
    return { name: 'KycSubmissionPending', params: { submissionId: submission.id } };
  }

  return { name: 'KycCardDetails' };
}
