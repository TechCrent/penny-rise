import React, { useState, useCallback, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import Animated from 'react-native-reanimated';
import * as FileSystem from 'expo-file-system/legacy';
import * as Crypto from 'expo-crypto';

import type { RootStackParamList } from '../navigation/RootNavigator';
import {
  DocumentUploadSlot,
  type SelectedImage,
  type UploadState,
} from '../components/DocumentUploadSlot';
import { PrimaryButton } from '../components/PrimaryButton';
import { GradientHero, Icon, fadeInUp } from '../components/ui';
import { extractApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { decodeUserIdFromJwt } from '../auth/jwt';
import { uploadDocumentToSignedUrl, confirmDocumentUpload } from '../api/kyc';
import { saveKycSubmission, loadKycSubmission, clearKycSubmission } from '../storage/kycStorage';
import { colors, radii, shadows, spacing, typography } from '../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'KycDocumentUpload'>;
type Route = RouteProp<RootStackParamList, 'KycDocumentUpload'>;

type DocType = 'FRONT_OF_CARD' | 'BACK_OF_CARD' | 'SELFIE';

function inferImageContentType(image: SelectedImage): string {
  if (image.mimeType?.startsWith('image/')) {
    return image.mimeType;
  }

  const lowerName = image.fileName?.toLowerCase() ?? image.uri.toLowerCase();
  if (lowerName.endsWith('.png')) return 'image/png';
  if (lowerName.endsWith('.webp')) return 'image/webp';
  if (lowerName.endsWith('.heic')) return 'image/heic';
  if (lowerName.endsWith('.heif')) return 'image/heif';
  return 'image/jpeg';
}

interface DocumentState {
  state: UploadState;
  progress: number;
  previewUri: string | undefined;
  error: string | undefined;
}

const INITIAL_DOC_STATE: DocumentState = {
  state: 'idle',
  progress: 0,
  previewUri: undefined,
  error: undefined,
};

const DOC_CONFIG: Array<{ type: DocType; label: string; description: string }> = [
  {
    type: 'FRONT_OF_CARD',
    label: 'Front of Ghana Card',
    description: 'Take a clear photo of the front. Ensure all text is readable.',
  },
  {
    type: 'BACK_OF_CARD',
    label: 'Back of Ghana Card',
    description: 'Take a clear photo of the back.',
  },
  {
    type: 'SELFIE',
    label: 'Selfie with card',
    description: 'Hold your Ghana Card next to your face and take a photo.',
  },
];

export default function KycDocumentUploadScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const { accessToken } = useAuth();
  const { submissionId, uploadUrls } = route.params;

  const [docStates, setDocStates] = useState<Record<DocType, DocumentState>>({
    FRONT_OF_CARD: INITIAL_DOC_STATE,
    BACK_OF_CARD: INITIAL_DOC_STATE,
    SELFIE: INITIAL_DOC_STATE,
  });

  useEffect(() => {
    const currentUserId = accessToken ? decodeUserIdFromJwt(accessToken) : null;

    loadKycSubmission().then(async stored => {
      const isSameSubmission = stored?.submissionId === submissionId;
      const isSameUser =
        !!stored?.ownerUserId && !!currentUserId && stored.ownerUserId === currentUserId;

      if (!stored) return;
      if (!isSameSubmission || !isSameUser) {
        await clearKycSubmission();
        return;
      }
      if (!stored.uploadedTypes.length) return;

      setDocStates(prev => {
        const next = { ...prev };
        for (const type of stored.uploadedTypes) {
          next[type] = {
            state: 'success',
            progress: 1,
            previewUri: undefined,
            error: undefined,
          };
        }
        return next;
      });
    });
  }, [accessToken, submissionId]);

  const allUploaded = Object.values(docStates).every(d => d.state === 'success');

  const updateDocState = useCallback((type: DocType, update: Partial<DocumentState>) => {
    setDocStates(prev => ({
      ...prev,
      [type]: { ...prev[type], ...update },
    }));
  }, []);

  const handleDocumentSelected = useCallback(
    async (type: DocType, image: SelectedImage) => {
      updateDocState(type, {
        state: 'uploading',
        progress: 0,
        previewUri: image.uri,
        error: undefined,
      });

      try {
        const signedUrl = uploadUrls[type];
        const contentType = inferImageContentType(image);

        await uploadDocumentToSignedUrl(signedUrl, image.uri, contentType, progress => {
          updateDocState(type, { progress });
        });

        const base64Content = await FileSystem.readAsStringAsync(image.uri, {
          encoding: FileSystem.EncodingType.Base64,
        });
        const sha256Hash = await Crypto.digestStringAsync(
          Crypto.CryptoDigestAlgorithm.SHA256,
          base64Content,
        );

        const fileInfo = await FileSystem.getInfoAsync(image.uri);
        const sizeBytes = fileInfo.exists ? fileInfo.size : 0;

        const url = new URL(signedUrl);
        const storageKey = url.pathname.replace(/^\/internal\/local-storage\/upload\//, '');

        await confirmDocumentUpload(submissionId, {
          provider_event_id: `mobile-${submissionId}-${type}-${Date.now()}`,
          document_type: type,
          storage_key: storageKey,
          content_type: contentType,
          size_bytes: sizeBytes,
          sha256_hash: sha256Hash,
        });

        updateDocState(type, { state: 'success', progress: 1 });

        const stored = await loadKycSubmission();
        if (stored && stored.submissionId === submissionId) {
          const uploadedTypes = [...new Set([...stored.uploadedTypes, type])] as DocType[];
          await saveKycSubmission({
            ...stored,
            ownerUserId: accessToken
              ? (decodeUserIdFromJwt(accessToken) ?? stored.ownerUserId)
              : stored.ownerUserId,
            uploadedTypes,
          });
        }
      } catch (err) {
        console.error(err);
        const apiError = extractApiError(err);
        const message =
          apiError?.message ??
          (err instanceof Error ? err.message : 'Upload failed. Please retry.');
        updateDocState(type, { state: 'error', error: message });
      }
    },
    [accessToken, submissionId, uploadUrls, updateDocState],
  );

  const handleContinue = () => {
    // reset (not navigate) so KycCardDetails/KycDocumentUpload drop off the
    // stack — otherwise the back gesture/button would return the user to the
    // still-mounted form screens, where they could edit and resubmit while
    // the original submission is already under review.
    navigation.reset({
      index: 0,
      routes: [{ name: 'KycSubmissionPending', params: { submissionId } }],
    });
  };

  const uploadedCount = Object.values(docStates).filter(d => d.state === 'success').length;

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <Animated.View entering={fadeInUp(30)} style={styles.stepBadge}>
          <Icon name="lock-closed" size={12} color={colors.gold.text} />
          <Text style={styles.stepText}>Step 2 of 3</Text>
        </Animated.View>

        <Animated.View entering={fadeInUp(50)}>
          <GradientHero icon="shield-checkmark-outline" title="Upload your documents" />
        </Animated.View>

        <Animated.View entering={fadeInUp(110)} style={styles.progressCard}>
          <View style={styles.progressRow}>
            <Text style={styles.progressText}>
              {uploadedCount < 3
                ? `${uploadedCount} of 3 documents uploaded`
                : 'All 3 documents uploaded'}
            </Text>
            {allUploaded ? (
              <Icon name="checkmark-circle" size={18} color={colors.status.success} />
            ) : null}
          </View>
          <View style={styles.segments}>
            {[0, 1, 2].map(i => (
              <View
                key={i}
                style={[styles.segment, i < uploadedCount ? styles.segmentFilled : null]}
              />
            ))}
          </View>
        </Animated.View>

        {DOC_CONFIG.map((doc, index) => (
          <Animated.View key={doc.type} entering={fadeInUp(150 + index * 60)}>
            <DocumentUploadSlot
              label={doc.label}
              description={doc.description}
              state={docStates[doc.type].state}
              progress={docStates[doc.type].progress}
              previewUri={docStates[doc.type].previewUri}
              error={docStates[doc.type].error}
              onImageSelected={uri => handleDocumentSelected(doc.type, uri)}
            />
          </Animated.View>
        ))}

        <PrimaryButton
          title="Submit for review"
          onPress={handleContinue}
          disabled={!allUploaded}
          style={styles.submitButton}
        />

        {!allUploaded ? <Text style={styles.hint}>Upload all 3 documents to continue.</Text> : null}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  scroll: {
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.lg,
    paddingBottom: spacing['4xl'],
  },
  stepBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    alignSelf: 'flex-start',
    gap: spacing.xs,
    backgroundColor: colors.gold.light,
    borderRadius: radii.pill,
    paddingVertical: spacing.xs,
    paddingHorizontal: spacing.md,
    marginBottom: spacing.lg,
  },
  stepText: { ...typography.label, color: colors.gold.text },
  progressCard: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    marginBottom: spacing.xl,
    ...shadows.sm,
  },
  progressRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.md,
  },
  progressText: { ...typography.bodyMedium, color: colors.textPrimary },
  segments: { flexDirection: 'row', gap: spacing.sm },
  segment: {
    flex: 1,
    height: 6,
    borderRadius: radii.pill,
    backgroundColor: colors.neutral[200],
  },
  segmentFilled: { backgroundColor: colors.gold.base },
  submitButton: { marginTop: spacing.sm },
  hint: { textAlign: 'center', color: colors.textTertiary, fontSize: 13, marginTop: spacing.md },
});
