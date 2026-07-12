import { getAccessToken } from '../auth/authSession';
import { decodeUserIdFromJwt } from '../auth/jwt';
import { getMySubmission } from '../api/kyc';
import {
  clearKycSubmission,
  hasAcknowledgedKycApproval,
  loadKycSubmission,
} from '../storage/kycStorage';
import type { RootStackParamList } from './RootNavigator';

export type PostAuthRoute = {
  [K in keyof RootStackParamList]: RootStackParamList[K] extends undefined
    ? { name: K }
    : { name: K; params: RootStackParamList[K] };
}[keyof Pick<
  RootStackParamList,
  'Main' | 'KycCardDetails' | 'KycDocumentUpload' | 'KycSubmissionPending'
>];

const ACTIVE_SUBMISSION_STATUSES = ['REVIEWING', 'SUBMITTED'];

export async function resolvePostAuthNavigation(kycStatus: string): Promise<PostAuthRoute> {
  const accessToken = await getAccessToken();

  const [stored] = await Promise.all([loadKycSubmission()]);
  const currentUserId = accessToken ? decodeUserIdFromJwt(accessToken) : null;
  const submission = await getMySubmission();

  if (stored) {
    const belongsToCurrentUser =
      !!stored.ownerUserId && !!currentUserId && stored.ownerUserId === currentUserId;
    const matchesActivePendingSubmission =
      submission?.id === stored.submissionId && submission.status === 'PENDING_DOCUMENTS';

    if (belongsToCurrentUser && matchesActivePendingSubmission) {
      return {
        name: 'KycDocumentUpload',
        params: {
          submissionId: stored.submissionId,
          uploadUrls: stored.uploadUrls,
        },
      };
    }

    await clearKycSubmission();
  }

  if (kycStatus === 'APPROVED') {
    const acknowledged = await hasAcknowledgedKycApproval();
    if (!acknowledged && submission) {
      return { name: 'KycSubmissionPending', params: { submissionId: submission.id } };
    }
    return { name: 'Main', params: { screen: 'Home' } };
  }

  if (kycStatus === 'SUBMITTED') {
    if (submission) {
      return { name: 'KycSubmissionPending', params: { submissionId: submission.id } };
    }
    return { name: 'Main', params: { screen: 'Home' } };
  }

  if (submission && ACTIVE_SUBMISSION_STATUSES.includes(submission.status)) {
    return { name: 'KycSubmissionPending', params: { submissionId: submission.id } };
  }

  return { name: 'KycCardDetails' };
}
