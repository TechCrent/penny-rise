import React, { useState, useRef } from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import Animated from 'react-native-reanimated';

import { type RootStackParamList } from '../navigation/RootNavigator';
import { registerSchema, type RegisterFormValues } from './RegisterScreen.schema';
import { FormField } from '../components/FormField';
import { PrimaryButton } from '../components/PrimaryButton';
import { GradientHero, Icon, PressableScale, fadeInUp } from '../components/ui';
import { signup } from '../api/auth';
import { extractApiError } from '../api/client';
import { colors, radii, shadows, spacing } from '../theme';

type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'Register'>;

export default function RegisterScreen() {
  const navigation = useNavigation<NavigationProp>();
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [showReferralField, setShowReferralField] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const submittingRef = useRef(false);

  const {
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      displayName: '',
      email: '',
      password: '',
      confirmPassword: '',
      referralCode: '',
      termsAccepted: false,
    },
  });

  const onSubmit = async (values: RegisterFormValues) => {
    if (submittingRef.current) return;
    submittingRef.current = true;
    setIsSubmitting(true);

    try {
      await signup({
        display_name: values.displayName,
        email: values.email,
        password: values.password,
        referral_code: values.referralCode || undefined,
        terms_accepted: values.termsAccepted,
      });

      navigation.navigate('EmailVerificationPending', { email: values.email });
    } catch (error) {
      console.error(error);
      const apiError = extractApiError(error);

      if (apiError?.code === 'AUTH_EMAIL_ALREADY_REGISTERED') {
        setError('email', { message: 'An account with this email already exists.' });
      } else if (apiError?.code === 'VALIDATION_ERROR' && apiError.details) {
        for (const [field, message] of Object.entries(apiError.details)) {
          const fieldMap: Record<string, keyof RegisterFormValues> = {
            email: 'email',
            password: 'password',
            displayName: 'displayName',
          };
          const formField = fieldMap[field];
          if (formField) {
            setError(formField, { message: message as string });
          }
        }
      } else {
        setError('email', {
          message: apiError?.message ?? 'Something went wrong. Please try again.',
        });
      }
    } finally {
      submittingRef.current = false;
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
              title="Create your account"
              subtitle="Start saving smarter with Stash."
            />
          </Animated.View>

          <Animated.View entering={fadeInUp(110)} style={styles.formCard}>
            <Controller
              control={control}
              name="displayName"
              render={({ field: { onChange, onBlur, value } }) => (
                <FormField
                  label="Display name"
                  placeholder="How should we call you?"
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  error={errors.displayName?.message}
                  returnKeyType="next"
                />
              )}
            />

            <Controller
              control={control}
              name="email"
              render={({ field: { onChange, onBlur, value } }) => (
                <FormField
                  label="Email address"
                  placeholder="you@example.com"
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  error={errors.email?.message}
                  keyboardType="email-address"
                  textContentType="emailAddress"
                  returnKeyType="next"
                />
              )}
            />

            <Controller
              control={control}
              name="password"
              render={({ field: { onChange, onBlur, value } }) => (
                <FormField
                  label="Password"
                  placeholder="Min. 8 characters"
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  error={errors.password?.message}
                  secureTextEntry={!showPassword}
                  textContentType="newPassword"
                  returnKeyType="done"
                  rightElement={
                    <TouchableOpacity
                      onPress={() => setShowPassword(v => !v)}
                      style={styles.eyeButton}
                      hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                      accessibilityRole="button"
                      accessibilityLabel={showPassword ? 'Hide password' : 'Show password'}
                    >
                      <Icon
                        name={showPassword ? 'eye-off' : 'eye'}
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
                  label="Confirm password"
                  placeholder="Re-enter your password"
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  error={errors.confirmPassword?.message}
                  secureTextEntry={!showConfirmPassword}
                  textContentType="newPassword"
                  returnKeyType="done"
                  rightElement={
                    <TouchableOpacity
                      onPress={() => setShowConfirmPassword(v => !v)}
                      style={styles.eyeButton}
                      hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                      accessibilityRole="button"
                      accessibilityLabel={showConfirmPassword ? 'Hide password' : 'Show password'}
                    >
                      <Icon
                        name={showConfirmPassword ? 'eye-off' : 'eye'}
                        size={20}
                        color={colors.textTertiary}
                      />
                    </TouchableOpacity>
                  }
                />
              )}
            />

            {!showReferralField ? (
              <TouchableOpacity
                onPress={() => setShowReferralField(true)}
                style={styles.referralToggle}
                hitSlop={8}
              >
                <Icon name="gift-outline" size={16} color={colors.gold.text} />
                <Text style={styles.referralToggleText}>Have a referral code?</Text>
              </TouchableOpacity>
            ) : (
              <Controller
                control={control}
                name="referralCode"
                render={({ field: { onChange, onBlur, value } }) => (
                  <FormField
                    label="Referral code (optional)"
                    placeholder="e.g. FRIEND50"
                    value={value}
                    onChangeText={onChange}
                    onBlur={onBlur}
                    error={errors.referralCode?.message}
                    autoCapitalize="characters"
                    returnKeyType="done"
                  />
                )}
              />
            )}

            <Controller
              control={control}
              name="termsAccepted"
              render={({ field: { onChange, value } }) => (
                <View>
                  <PressableScale
                    style={styles.termsRow}
                    onPress={() => onChange(!value)}
                    accessibilityRole="checkbox"
                    accessibilityState={{ checked: value }}
                  >
                    <View style={[styles.checkbox, value ? styles.checkboxChecked : null]}>
                      {value ? (
                        <Icon name="checkmark" size={13} color={colors.neutral[900]} />
                      ) : null}
                    </View>
                    <Text style={styles.termsText}>
                      I agree to the{' '}
                      <Text style={styles.termsLink} onPress={() => navigation.navigate('Legal')}>
                        Terms of Service and Privacy Policy
                      </Text>
                    </Text>
                  </PressableScale>
                  {errors.termsAccepted ? (
                    <Text style={styles.termsError}>{errors.termsAccepted.message}</Text>
                  ) : null}
                </View>
              )}
            />

            <PrimaryButton
              title="Create account"
              onPress={handleSubmit(onSubmit)}
              loading={isSubmitting}
              style={styles.submitButton}
            />
          </Animated.View>

          <Animated.View entering={fadeInUp(180)} style={styles.loginRow}>
            <Text style={styles.loginText}>Already have an account? </Text>
            <TouchableOpacity onPress={() => navigation.navigate('Login')} hitSlop={8}>
              <Text style={styles.loginLink}>Sign in</Text>
            </TouchableOpacity>
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

  eyeButton: { paddingHorizontal: spacing.md },
  referralToggle: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
    marginBottom: spacing.lg,
  },
  referralToggleText: { color: colors.gold.text, fontSize: 14, fontWeight: '600' },
  termsRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginTop: spacing.xs,
    marginBottom: spacing.xs,
  },
  checkbox: {
    width: 22,
    height: 22,
    borderRadius: 6,
    borderWidth: 1.5,
    borderColor: colors.borderStrong,
    marginRight: spacing.sm,
    marginTop: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkboxChecked: { backgroundColor: colors.gold.base, borderColor: colors.gold.base },
  termsText: { flex: 1, fontSize: 13, color: colors.neutral[700], lineHeight: 19 },
  termsLink: { color: colors.gold.text, fontWeight: '700' },
  termsError: { color: colors.status.error, fontSize: 12, marginTop: spacing.xs, marginLeft: 30 },
  submitButton: { marginTop: spacing.lg },

  loginRow: { flexDirection: 'row', justifyContent: 'center', marginTop: spacing['2xl'] },
  loginText: { color: colors.textSecondary, fontSize: 14 },
  loginLink: { color: colors.gold.text, fontSize: 14, fontWeight: '700' },
});
