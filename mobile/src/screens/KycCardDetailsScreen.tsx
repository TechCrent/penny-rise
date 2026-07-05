import React, { useState } from 'react';
import { View, Text, StyleSheet, ScrollView, KeyboardAvoidingView, Platform } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

import type { RootStackParamList } from '../navigation/RootNavigator';
import { FormField } from '../components/FormField';
import { PrimaryButton } from '../components/PrimaryButton';
import { createSubmission } from '../api/kyc';
import { saveKycSubmission } from '../storage/kycStorage';
import { extractApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { decodeUserIdFromJwt } from '../auth/jwt';
import { useKycResumability } from '../hooks/useKycResumability';

type Nav = NativeStackNavigationProp<RootStackParamList, 'KycCardDetails'>;

const schema = z.object({
  ghanaCardNumber: z
    .string()
    .min(1, 'Ghana Card number is required')
    .regex(/^GHA-[0-9]{9}-[0-9]$/, 'Format must be GHA-XXXXXXXXX-X (e.g. GHA-000000001-1)'),
  fullName: z
    .string()
    .min(1, 'Full name is required')
    .max(255, 'Name must not exceed 255 characters'),
});
type FormValues = z.infer<typeof schema>;

export default function KycCardDetailsScreen() {
  const navigation = useNavigation<Nav>();
  const { accessToken } = useAuth();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [globalError, setGlobalError] = useState<string | null>(null);

  useKycResumability();

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (values: FormValues) => {
    if (isSubmitting) return;
    setIsSubmitting(true);
    setGlobalError(null);

    try {
      const response = await createSubmission({
        ghana_card_number: values.ghanaCardNumber,
        full_name: values.fullName,
      });

      await saveKycSubmission({
        submissionId: response.id,
        ownerUserId: accessToken ? (decodeUserIdFromJwt(accessToken) ?? undefined) : undefined,
        uploadUrls: response.upload_urls,
        uploadedTypes: [],
      });

      navigation.navigate('KycDocumentUpload', {
        submissionId: response.id,
        uploadUrls: response.upload_urls,
      });
    } catch (error) {
      console.error(error);
      const apiError = extractApiError(error);

      if (apiError?.code === 'KYC_SUBMISSION_ALREADY_ACTIVE') {
        setGlobalError(
          'You already have an active KYC submission. Please wait for it to be processed.',
        );
      } else {
        setGlobalError(apiError?.message ?? 'Something went wrong. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.flex}
      >
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
          <View style={styles.stepIndicator}>
            <Text style={styles.stepText}>Step 1 of 3</Text>
          </View>

          <Text style={styles.heading}>Your Ghana Card details</Text>
          <Text style={styles.subheading}>
            Enter your details exactly as they appear on your Ghana Card.
          </Text>

          {globalError ? (
            <View style={styles.globalError}>
              <Text style={styles.globalErrorText}>{globalError}</Text>
            </View>
          ) : null}

          <Controller
            control={control}
            name="ghanaCardNumber"
            render={({ field: { onChange, onBlur, value } }) => (
              <FormField
                label="Ghana Card number"
                placeholder="GHA-000000000-0"
                value={value}
                onChangeText={text => onChange(text.toUpperCase())}
                onBlur={onBlur}
                error={errors.ghanaCardNumber?.message}
                autoCapitalize="characters"
                returnKeyType="next"
              />
            )}
          />

          <Controller
            control={control}
            name="fullName"
            render={({ field: { onChange, onBlur, value } }) => (
              <FormField
                label="Full name (as on card)"
                placeholder="e.g. KWAME MENSAH ASANTE"
                value={value}
                onChangeText={onChange}
                onBlur={onBlur}
                error={errors.fullName?.message}
                autoCapitalize="characters"
                returnKeyType="done"
              />
            )}
          />

          <View style={styles.infoBox}>
            <Text style={styles.infoText}>
              Your documents are encrypted and used only for identity verification. They are
              automatically deleted 24 hours after a decision is made.
            </Text>
          </View>

          <PrimaryButton
            title="Continue"
            onPress={handleSubmit(onSubmit)}
            loading={isSubmitting}
            style={styles.submitButton}
          />
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#FFFFFF' },
  flex: { flex: 1 },
  scroll: { paddingHorizontal: 24, paddingTop: 48, paddingBottom: 40 },
  stepIndicator: { marginBottom: 24 },
  stepText: { fontSize: 13, color: '#9CA3AF', fontWeight: '500' },
  heading: { fontSize: 26, fontWeight: '700', color: '#111827', marginBottom: 8 },
  subheading: { fontSize: 15, color: '#6B7280', marginBottom: 28 },
  globalError: { backgroundColor: '#FEF2F2', borderRadius: 8, padding: 14, marginBottom: 20 },
  globalErrorText: { color: '#991B1B', fontSize: 14 },
  infoBox: {
    backgroundColor: '#F0FDF4',
    borderRadius: 8,
    padding: 14,
    marginBottom: 24,
    borderWidth: 1,
    borderColor: '#BBF7D0',
  },
  infoText: { fontSize: 13, color: '#166534', lineHeight: 20 },
  submitButton: { marginTop: 4 },
});
