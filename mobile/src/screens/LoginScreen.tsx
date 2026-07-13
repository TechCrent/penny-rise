import React, { useState, useEffect, useRef } from 'react';
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
import { login } from '../api/auth';
import { extractApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { isKycUnderReviewBannerPending } from '../storage/kycStorage';
import { colors, radii, shadows, spacing } from '../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Login'>;
type Route = RouteProp<RootStackParamList, 'Login'>;

const loginSchema = z.object({
  email: z.string().min(1, 'Email is required').email('Invalid email address'),
  password: z.string().min(1, 'Password is required'),
});
type LoginFormValues = z.infer<typeof loginSchema>;

export default function LoginScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const { setTokens } = useAuth();

  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [globalError, setGlobalError] = useState<string | null>(null);
  const [lockoutSecondsRemaining, setLockoutSeconds] = useState(0);
  const [showUnderReviewBanner, setShowUnderReviewBanner] = useState(false);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(
    () => () => {
      if (countdownRef.current) clearInterval(countdownRef.current);
    },
    [],
  );

  useEffect(() => {
    isKycUnderReviewBannerPending().then(setShowUnderReviewBanner);
  }, []);

  const startLockoutCountdown = (seconds: number) => {
    setLockoutSeconds(seconds);
    countdownRef.current = setInterval(() => {
      setLockoutSeconds(s => {
        if (s <= 1) {
          clearInterval(countdownRef.current!);
          return 0;
        }
        return s - 1;
      });
    }, 1000);
  };

  const {
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) });

  const successBanner = route.params?.successBanner;

  const onSubmit = async (values: LoginFormValues) => {
    if (isSubmitting || lockoutSecondsRemaining > 0) return;
    setIsSubmitting(true);
    setGlobalError(null);

    try {
      const response = await login({ email: values.email, password: values.password });
      await setTokens(response.access_token, response.refresh_token, response.user.kyc_status);
      // RootNavigator switches to the authenticated stack; AuthenticatedBootstrap
      // resolves the correct post-login destination.
    } catch (error) {
      console.error(error);
      const apiError = extractApiError(error);

      if (apiError?.code === 'AUTH_ACCOUNT_LOCKED') {
        const retryAfter = (apiError.details?.retry_after as unknown as number) ?? 3600;
        startLockoutCountdown(retryAfter);
        setGlobalError(null);
      } else if (apiError?.code === 'AUTH_INVALID_CREDENTIALS') {
        setError('password', { message: 'Email or password is incorrect.' });
      } else if (apiError?.code === 'AUTH_EMAIL_NOT_VERIFIED') {
        setGlobalError('Please verify your email before logging in.');
      } else if (apiError?.code === 'AUTH_ACCOUNT_SUSPENDED') {
        setGlobalError('Your account has been suspended. Contact support.');
      } else {
        setGlobalError(apiError?.message ?? 'Something went wrong. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const lockoutMinutes = Math.floor(lockoutSecondsRemaining / 60);
  const lockoutSeconds = lockoutSecondsRemaining % 60;
  const isLocked = lockoutSecondsRemaining > 0;

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
            <GradientHero showLogo title="Welcome back" subtitle="Sign in to your Stash account." />
          </Animated.View>

          <Animated.View entering={fadeInUp(110)} style={styles.formCard}>
            {successBanner ? <Banner tone="success" message={successBanner} /> : null}

            {showUnderReviewBanner ? (
              <Banner
                tone="info"
                message="Your KYC verification is under review. Log in to check your status."
              />
            ) : null}

            {globalError ? <Banner tone="error" message={globalError} /> : null}

            {isLocked ? (
              <Banner
                tone="warning"
                icon="lock-closed"
                title="Account temporarily locked"
                message={`Too many failed attempts. Try again in ${lockoutMinutes}:${lockoutSeconds
                  .toString()
                  .padStart(2, '0')}`}
              />
            ) : null}

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
                  editable={!isLocked}
                />
              )}
            />

            <Controller
              control={control}
              name="password"
              render={({ field: { onChange, onBlur, value } }) => (
                <FormField
                  label="Password"
                  placeholder="Your password"
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  error={errors.password?.message}
                  secureTextEntry={!showPassword}
                  textContentType="password"
                  editable={!isLocked}
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

            <TouchableOpacity
              onPress={() => navigation.navigate('ForgotPassword')}
              style={styles.forgotRow}
              hitSlop={8}
            >
              <Text style={styles.forgotText}>Forgot password?</Text>
            </TouchableOpacity>

            <PrimaryButton
              title={
                isLocked
                  ? `Locked (${lockoutMinutes}:${lockoutSeconds.toString().padStart(2, '0')})`
                  : 'Sign in'
              }
              onPress={handleSubmit(onSubmit)}
              loading={isSubmitting}
              disabled={isLocked}
              style={styles.submitButton}
            />
          </Animated.View>

          <Animated.View entering={fadeInUp(180)} style={styles.registerRow}>
            <Text style={styles.registerText}>Don&apos;t have an account? </Text>
            <TouchableOpacity onPress={() => navigation.navigate('Register')} hitSlop={8}>
              <Text style={styles.registerLink}>Register</Text>
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
  forgotRow: { alignSelf: 'flex-end', marginBottom: spacing.lg, marginTop: -spacing.xs },
  forgotText: { color: colors.gold.text, fontSize: 14, fontWeight: '600' },
  submitButton: { marginTop: spacing.xxs },
  registerRow: { flexDirection: 'row', justifyContent: 'center', marginTop: spacing['2xl'] },
  registerText: { color: colors.textSecondary, fontSize: 14 },
  registerLink: { color: colors.gold.text, fontSize: 14, fontWeight: '700' },
});
