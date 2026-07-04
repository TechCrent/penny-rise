import { useEffect } from 'react';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { getAccessToken } from '../auth/authSession';
import { decodeUserIdFromJwt } from '../auth/jwt';
import type { RootStackParamList } from '../navigation/RootNavigator';
import { getMySubmission } from '../api/kyc';
import { clearKycSubmission, loadKycSubmission } from '../storage/kycStorage';

type Nav = NativeStackNavigationProp<RootStackParamList>;

interface UseKycResumabilityOptions {
  enabled?: boolean;
}

export function useKycResumability(options?: UseKycResumabilityOptions) {
  const navigation = useNavigation<Nav>();
  const enabled = options?.enabled ?? true;

  useEffect(() => {
    if (!enabled) return;

    async function check() {
      const [stored, accessToken] = await Promise.all([loadKycSubmission(), getAccessToken()]);
      const currentUserId = accessToken ? decodeUserIdFromJwt(accessToken) : null;
      const submission = await getMySubmission();

      if (stored) {
        const belongsToCurrentUser =
          !!stored.ownerUserId && !!currentUserId && stored.ownerUserId === currentUserId;
        const matchesActivePendingSubmission =
          submission?.id === stored.submissionId && submission.status === 'PENDING_DOCUMENTS';

        if (belongsToCurrentUser && matchesActivePendingSubmission) {
          navigation.replace('KycDocumentUpload', {
            submissionId: stored.submissionId,
            uploadUrls: stored.uploadUrls,
          });
          return;
        }

        await clearKycSubmission();
      }

      if (submission && ['REVIEWING', 'SUBMITTED'].includes(submission.status)) {
        navigation.replace('KycSubmissionPending', { submissionId: submission.id });
      }
    }

    check();
  }, [navigation, enabled]);
}
