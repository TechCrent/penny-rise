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
import Animated, { FadeInUp } from 'react-native-reanimated';

import type { RootStackParamList } from '../navigation/RootNavigator';
import { FormField } from '../components/FormField';
import { PrimaryButton } from '../components/PrimaryButton';
import { login } from '../api/auth';
import { extractApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { isKycUnderReviewBannerPending } from '../storage/kycStorage';
import { colors, radii, spacing, typography } from '../theme';

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
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
          <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backRow} hitSlop={8}>
            <Text style={styles.backText}>← Back</Text>
          </TouchableOpacity>

          {successBanner ? (
            <Animated.View entering={FadeInUp.duration(300)} style={styles.successBanner}>
              <Text style={styles.successBannerText}>{successBanner}</Text>
            </Animated.View>
          ) : null}

          {showUnderReviewBanner ? (
            <View style={styles.reviewBanner}>
              <Text style={styles.reviewBannerText}>
                Your KYC verification is under review. Log in to check your status.
              </Text>
            </View>
          ) : null}

          <Text style={styles.heading}>Welcome back</Text>
          <Text style={styles.subheading}>Sign in to your Stash account.</Text>

          {globalError ? (
            <Animated.View entering={FadeInUp.duration(300)} style={styles.globalError}>
              <Text style={styles.globalErrorText}>{globalError}</Text>
            </Animated.View>
          ) : null}

          {isLocked ? (
            <Animated.View entering={FadeInUp.duration(300)} style={styles.lockoutBox}>
              <Text style={styles.lockoutTitle}>Account temporarily locked</Text>
              <Text style={styles.lockoutBody}>
                Too many failed attempts. Try again in{' '}
                <Text style={styles.lockoutTimer}>
                  {lockoutMinutes}:{lockoutSeconds.toString().padStart(2, '0')}
                </Text>
              </Text>
            </Animated.View>
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
                  >
                    <Text style={styles.eyeText}>{showPassword ? 'Hide' : 'Show'}</Text>
                  </TouchableOpacity>
                }
              />
            )}
          />

          <TouchableOpacity
            onPress={() => navigation.navigate('ForgotPassword')}
            style={styles.forgotRow}
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

          <View style={styles.registerRow}>
            <Text style={styles.registerText}>Don&apos;t have an account? </Text>
            <TouchableOpacity onPress={() => navigation.navigate('Register')}>
              <Text style={styles.registerLink}>Register</Text>
            </TouchableOpacity>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  flex: { flex: 1 },
  scroll: { paddingHorizontal: spacing.xl, paddingTop: spacing['5xl'], paddingBottom: spacing['4xl'] },
  backRow: { marginBottom: spacing.xl },
  backText: { color: colors.textPrimary, fontSize: 15 },
  heading: { ...typography.h1, color: colors.textPrimary, marginBottom: spacing.sm },
  subheading: { fontSize: 16, color: colors.textSecondary, marginBottom: spacing['3xl'] },
  successBanner: {
    backgroundColor: colors.status.successBg,
    borderRadius: radii.sm,
    padding: spacing.md,
    marginBottom: spacing.xl,
  },
  successBannerText: { color: colors.status.successText, fontSize: 14, fontWeight: '500' },
  reviewBanner: {
    backgroundColor: colors.status.infoBg,
    borderRadius: radii.sm,
    padding: spacing.md,
    marginBottom: spacing.xl,
    borderWidth: 1,
    borderColor: '#BFDBFE',
  },
  reviewBannerText: { color: colors.status.infoText, fontSize: 14, fontWeight: '500' },
  globalError: {
    backgroundColor: colors.status.errorBg,
    borderRadius: radii.sm,
    padding: spacing.md,
    marginBottom: spacing.xl,
  },
  globalErrorText: { color: colors.status.errorText, fontSize: 14 },
  lockoutBox: {
    backgroundColor: colors.status.warningBg,
    borderRadius: radii.sm,
    padding: spacing.lg,
    marginBottom: spacing.xl,
    borderWidth: 1,
    borderColor: '#FED7AA',
  },
  lockoutTitle: { fontSize: 15, fontWeight: '600', color: colors.status.warningText, marginBottom: spacing.sm },
  lockoutBody: { fontSize: 14, color: colors.status.warningText },
  lockoutTimer: { fontWeight: '700', fontVariant: ['tabular-nums'] },
  eyeButton: { paddingHorizontal: spacing.md },
  eyeText: { color: colors.textSecondary, fontSize: 14 },
  forgotRow: { alignSelf: 'flex-end', marginBottom: spacing.xl, marginTop: -spacing.xs },
  forgotText: { color: colors.textPrimary, fontSize: 14, textDecorationLine: 'underline' },
  submitButton: { marginTop: spacing.xxs },
  registerRow: { flexDirection: 'row', justifyContent: 'center', marginTop: spacing.xl },
  registerText: { color: colors.textSecondary, fontSize: 14 },
  registerLink: { color: colors.textPrimary, fontSize: 14, fontWeight: '600' },
});
