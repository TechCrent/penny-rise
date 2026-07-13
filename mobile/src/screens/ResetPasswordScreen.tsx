import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  KeyboardAvoidingView,
  ScrollView,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import Animated from 'react-native-reanimated';

import type { RootStackParamList } from '../navigation/RootNavigator';
import { FormField } from '../components/FormField';
import { PrimaryButton } from '../components/PrimaryButton';
import { Banner, GradientHero, Icon, fadeInUp } from '../components/ui';
import { resetPassword } from '../api/auth';
import { extractApiError } from '../api/client';
import { colors, radii, shadows, spacing } from '../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'ResetPassword'>;
type Route = RouteProp<RootStackParamList, 'ResetPassword'>;

const schema = z
  .object({
    newPassword: z
      .string()
      .min(8, 'Password must be at least 8 characters')
      .regex(
        /^(?=.*[0-9])(?=.*[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]).{8,}$/,
        'Must contain at least one number and one special character',
      ),
    confirmPassword: z.string().min(1, 'Please confirm your new password'),
  })
  .refine(data => data.newPassword === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type FormValues = z.infer<typeof schema>;

export default function ResetPasswordScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const { token } = route.params;

  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [globalError, setGlobalError] = useState<string | null>(null);

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
      await resetPassword(token, values.newPassword);
      navigation.navigate('Login', {
        successBanner: 'Password reset successfully. Please sign in.',
      });
    } catch (error) {
      console.error(error);
      const apiError = extractApiError(error);

      if (apiError?.code === 'AUTH_RESET_TOKEN_EXPIRED') {
        setGlobalError('This reset link has expired. Please request a new one.');
      } else if (apiError?.code === 'AUTH_RESET_TOKEN_ALREADY_USED') {
        setGlobalError('This reset link has already been used. Please request a new one.');
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
          <TouchableOpacity
            onPress={() => navigation.goBack()}
            style={styles.backButton}
            hitSlop={8}
            accessibilityRole="button"
            accessibilityLabel="Go back"
          >
            <Icon name="chevron-back" size={22} color={colors.textPrimary} />
          </TouchableOpacity>

          <Animated.View entering={fadeInUp(40)}>
            <GradientHero
              showLogo
              title="Set a new password"
              subtitle="Choose a strong password with at least one number and one special character."
            />
          </Animated.View>

          <Animated.View entering={fadeInUp(110)} style={styles.formCard}>
            {globalError ? (
              <View>
                <Banner tone="error" message={globalError} />
                {globalError.includes('expired') || globalError.includes('already been used') ? (
                  <TouchableOpacity
                    onPress={() => navigation.navigate('ForgotPassword')}
                    style={styles.requestNewRow}
                    hitSlop={8}
                  >
                    <Text style={styles.requestNewLink}>Request a new reset link →</Text>
                  </TouchableOpacity>
                ) : null}
              </View>
            ) : null}

            <Controller
              control={control}
              name="newPassword"
              render={({ field: { onChange, onBlur, value } }) => (
                <FormField
                  label="New password"
                  placeholder="Min. 8 characters"
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  error={errors.newPassword?.message}
                  secureTextEntry={!showNew}
                  textContentType="newPassword"
                  rightElement={
                    <TouchableOpacity
                      onPress={() => setShowNew(v => !v)}
                      style={styles.eyeButton}
                      hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                      accessibilityRole="button"
                      accessibilityLabel={showNew ? 'Hide password' : 'Show password'}
                    >
                      <Icon
                        name={showNew ? 'eye-off' : 'eye'}
                        size={20}
                        color={colors.textTertiary}
                      />
                    </TouchableOpacity>
                  }
                />
              )}
            />

            <Controller
              control={control}
              name="confirmPassword"
              render={({ field: { onChange, onBlur, value } }) => (
                <FormField
                  label="Confirm new password"
                  placeholder="Repeat your new password"
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  error={errors.confirmPassword?.message}
                  secureTextEntry={!showConfirm}
                  textContentType="newPassword"
                  rightElement={
                    <TouchableOpacity
                      onPress={() => setShowConfirm(v => !v)}
                      style={styles.eyeButton}
                      hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                      accessibilityRole="button"
                      accessibilityLabel={showConfirm ? 'Hide password' : 'Show password'}
                    >
                      <Icon
                        name={showConfirm ? 'eye-off' : 'eye'}
                        size={20}
                        color={colors.textTertiary}
                      />
                    </TouchableOpacity>
                  }
                />
              )}
            />

            <PrimaryButton
              title="Set new password"
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
    paddingTop: spacing.md,
    paddingBottom: spacing['4xl'],
  },
  backButton: {
    width: 40,
    height: 40,
    borderRadius: radii.pill,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.xl,
  },
  formCard: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
    ...shadows.sm,
  },
  requestNewRow: { marginTop: -spacing.sm, marginBottom: spacing.lg },
  requestNewLink: {
    color: colors.status.errorText,
    fontSize: 13,
    fontWeight: '600',
    textDecorationLine: 'underline',
  },
  eyeButton: { paddingHorizontal: spacing.md },
  submitButton: { marginTop: spacing.xxs },
});
