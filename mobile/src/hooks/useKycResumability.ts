import { useEffect } from 'react';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../navigation/RootNavigator';
import { getMySubmission } from '../api/kyc';
import { loadKycSubmission } from '../storage/kycStorage';

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
      const stored = await loadKycSubmission();
      if (stored) {
        navigation.replace('KycDocumentUpload', {
          submissionId: stored.submissionId,
          uploadUrls: stored.uploadUrls,
        });
        return;
      }

      const submission = await getMySubmission();
      if (submission && ['REVIEWING', 'SUBMITTED'].includes(submission.status)) {
        navigation.replace('KycSubmissionPending', { submissionId: submission.id });
      }
    }

    check();
  }, [navigation, enabled]);
}
