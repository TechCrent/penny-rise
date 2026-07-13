import React, { useState } from 'react';
import { View, Text, StyleSheet, ScrollView, KeyboardAvoidingView, Platform } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import Animated, { FadeInUp } from 'react-native-reanimated';

import { Icon } from '../components/ui';
import type { RootStackParamList } from '../navigation/RootNavigator';
import { FormField } from '../components/FormField';
import { PrimaryButton } from '../components/PrimaryButton';
import { createSubmission } from '../api/kyc';
import { saveKycSubmission } from '../storage/kycStorage';
import { extractApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { decodeUserIdFromJwt } from '../auth/jwt';
import { useKycResumability } from '../hooks/useKycResumability';
import { formatGhanaCardInput } from '../utils/ghanaCard';
import { colors, radii, spacing, typography } from '../theme';

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
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { ghanaCardNumber: 'GHA-', fullName: '' },
  });

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
            <Animated.View entering={FadeInUp.duration(300)} style={styles.globalError}>
              <Text style={styles.globalErrorText}>{globalError}</Text>
            </Animated.View>
          ) : null}

          <Controller
            control={control}
            name="ghanaCardNumber"
            render={({ field: { onChange, onBlur, value } }) => (
              <FormField
                label="Ghana Card number"
                placeholder="GHA-000000000-0"
                value={value}
                onChangeText={text => onChange(formatGhanaCardInput(text))}
                onBlur={onBlur}
                error={errors.ghanaCardNumber?.message}
                autoCapitalize="characters"
                keyboardType="number-pad"
                maxLength={15}
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
            <Icon name="shield-checkmark-outline" size={16} color={colors.status.successText} />
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
  safe: { flex: 1, backgroundColor: colors.background },
  flex: { flex: 1 },
  scroll: { paddingHorizontal: spacing.xl, paddingTop: spacing['5xl'], paddingBottom: spacing['4xl'] },
  stepIndicator: { marginBottom: spacing.xl },
  stepText: { fontSize: 13, color: colors.textTertiary, fontWeight: '500' },
  heading: { ...typography.h1, fontSize: 26, color: colors.textPrimary, marginBottom: spacing.sm },
  subheading: { fontSize: 15, color: colors.textSecondary, marginBottom: spacing['2xl'] },
  globalError: {
    backgroundColor: colors.status.errorBg,
    borderRadius: radii.sm,
    padding: spacing.md,
    marginBottom: spacing.xl,
  },
  globalErrorText: { color: colors.status.errorText, fontSize: 14 },
  infoBox: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.sm,
    backgroundColor: colors.status.successBg,
    borderRadius: radii.md,
    padding: spacing.md,
    marginBottom: spacing.xl,
  },
  infoText: { flex: 1, fontSize: 13, color: colors.status.successText, lineHeight: 20 },
  submitButton: { marginTop: spacing.xxs },
});
