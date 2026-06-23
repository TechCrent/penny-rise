import React, { useState, useCallback, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import * as FileSystem from 'expo-file-system/legacy';
import * as Crypto from 'expo-crypto';

import type { RootStackParamList } from '../navigation/RootNavigator';
import { DocumentUploadSlot, type UploadState } from '../components/DocumentUploadSlot';
import { PrimaryButton } from '../components/PrimaryButton';
import { uploadDocumentToSignedUrl, confirmDocumentUpload } from '../api/kyc';
import { saveKycSubmission, loadKycSubmission } from '../storage/kycStorage';

type Nav = NativeStackNavigationProp<RootStackParamList, 'KycDocumentUpload'>;
type Route = RouteProp<RootStackParamList, 'KycDocumentUpload'>;

type DocType = 'FRONT_OF_CARD' | 'BACK_OF_CARD' | 'SELFIE';

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
  const { submissionId, uploadUrls } = route.params;

  const [docStates, setDocStates] = useState<Record<DocType, DocumentState>>({
    FRONT_OF_CARD: INITIAL_DOC_STATE,
    BACK_OF_CARD: INITIAL_DOC_STATE,
    SELFIE: INITIAL_DOC_STATE,
  });

  useEffect(() => {
    loadKycSubmission().then(stored => {
      if (!stored?.uploadedTypes.length) return;

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
  }, []);

  const allUploaded = Object.values(docStates).every(d => d.state === 'success');

  const updateDocState = useCallback((type: DocType, update: Partial<DocumentState>) => {
    setDocStates(prev => ({
      ...prev,
      [type]: { ...prev[type], ...update },
    }));
  }, []);

  const handleDocumentSelected = useCallback(
    async (type: DocType, uri: string) => {
      updateDocState(type, { state: 'uploading', progress: 0, previewUri: uri, error: undefined });

      try {
        const signedUrl = uploadUrls[type];
        const contentType = 'image/jpeg';

        await uploadDocumentToSignedUrl(signedUrl, uri, contentType, progress => {
          updateDocState(type, { progress });
        });

        const base64Content = await FileSystem.readAsStringAsync(uri, {
          encoding: FileSystem.EncodingType.Base64,
        });
        const sha256Hash = await Crypto.digestStringAsync(
          Crypto.CryptoDigestAlgorithm.SHA256,
          base64Content,
        );

        const fileInfo = await FileSystem.getInfoAsync(uri);
        const sizeBytes = fileInfo.exists ? fileInfo.size : 0;

        const url = new URL(signedUrl);
        const storageKey = url.pathname.replace('/storage/v1/object/sign/', '');

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
        if (stored) {
          const uploadedTypes = [...new Set([...stored.uploadedTypes, type])] as DocType[];
          await saveKycSubmission({ ...stored, uploadedTypes });
        }
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Upload failed. Please retry.';
        updateDocState(type, { state: 'error', error: message });
      }
    },
    [submissionId, uploadUrls, updateDocState],
  );

  const handleContinue = () => {
    navigation.navigate('KycSubmissionPending', { submissionId });
  };

  const uploadedCount = Object.values(docStates).filter(d => d.state === 'success').length;

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <View style={styles.stepIndicator}>
          <Text style={styles.stepText}>Step 2 of 3</Text>
        </View>

        <Text style={styles.heading}>Upload your documents</Text>
        <Text style={styles.subheading}>
          {uploadedCount < 3
            ? `${uploadedCount} of 3 documents uploaded`
            : 'All 3 documents uploaded'}
        </Text>

        {DOC_CONFIG.map(doc => (
          <DocumentUploadSlot
            key={doc.type}
            label={doc.label}
            description={doc.description}
            state={docStates[doc.type].state}
            progress={docStates[doc.type].progress}
            previewUri={docStates[doc.type].previewUri}
            error={docStates[doc.type].error}
            onImageSelected={uri => handleDocumentSelected(doc.type, uri)}
          />
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
  safe: { flex: 1, backgroundColor: '#FFFFFF' },
  scroll: { paddingHorizontal: 24, paddingTop: 48, paddingBottom: 40 },
  stepIndicator: { marginBottom: 24 },
  stepText: { fontSize: 13, color: '#9CA3AF', fontWeight: '500' },
  heading: { fontSize: 26, fontWeight: '700', color: '#111827', marginBottom: 8 },
  subheading: { fontSize: 15, color: '#6B7280', marginBottom: 28 },
  submitButton: { marginTop: 8 },
  hint: { textAlign: 'center', color: '#9CA3AF', fontSize: 13, marginTop: 12 },
});
