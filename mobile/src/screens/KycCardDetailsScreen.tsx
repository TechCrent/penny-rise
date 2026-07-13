import React, { useState } from 'react';
import { View, Text, StyleSheet, ScrollView, KeyboardAvoidingView, Platform } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import Animated from 'react-native-reanimated';

import type { RootStackParamList } from '../navigation/RootNavigator';
import { FormField } from '../components/FormField';
import { PrimaryButton } from '../components/PrimaryButton';
import { Banner, GradientHero, Icon, fadeInUp } from '../components/ui';
import { createSubmission } from '../api/kyc';
import { saveKycSubmission } from '../storage/kycStorage';
import { extractApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { decodeUserIdFromJwt } from '../auth/jwt';
import { useKycResumability } from '../hooks/useKycResumability';
import { formatGhanaCardInput } from '../utils/ghanaCard';
import { colors, radii, shadows, spacing, typography } from '../theme';

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
        <ScrollView
          contentContainerStyle={styles.scroll}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <Animated.View entering={fadeInUp(30)} style={styles.stepBadge}>
            <Icon name="lock-closed" size={12} color={colors.gold.text} />
            <Text style={styles.stepText}>Step 1 of 3</Text>
          </Animated.View>

          <Animated.View entering={fadeInUp(50)}>
            <GradientHero
              icon="shield-checkmark-outline"
              title="Your Ghana Card details"
              subtitle="Enter your details exactly as they appear on your Ghana Card."
            />
          </Animated.View>

          <Animated.View entering={fadeInUp(110)} style={styles.formCard}>
            {globalError ? <Banner tone="error" message={globalError} /> : null}

            <Banner
              tone="info"
              icon="shield-checkmark-outline"
              message="Your documents are encrypted and used only for identity verification. They are automatically deleted 24 hours after a decision is made."
            />

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

            <PrimaryButton
              title="Continue"
              onPress={handleSubmit(onSubmit)}
              loading={isSubmitting}
              style={styles.submitButton}
            />
          </Animated.View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  flex: { flex: 1 },
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
  formCard: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
    ...shadows.sm,
  },
  submitButton: { marginTop: spacing.xs },
});
