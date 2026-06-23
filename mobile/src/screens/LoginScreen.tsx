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

import type { RootStackParamList } from '../navigation/RootNavigator';
import { FormField } from '../components/FormField';
import { PrimaryButton } from '../components/PrimaryButton';
import { login } from '../api/auth';
import { extractApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';

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
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(
    () => () => {
      if (countdownRef.current) clearInterval(countdownRef.current);
    },
    [],
  );

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

      navigation.reset({ index: 0, routes: [{ name: 'Home' }] });
    } catch (error) {
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
          {successBanner ? (
            <View style={styles.successBanner}>
              <Text style={styles.successBannerText}>{successBanner}</Text>
            </View>
          ) : null}

          <Text style={styles.heading}>Welcome back</Text>
          <Text style={styles.subheading}>Sign in to your Stash account.</Text>

          {globalError ? (
            <View style={styles.globalError}>
              <Text style={styles.globalErrorText}>{globalError}</Text>
            </View>
          ) : null}

          {isLocked ? (
            <View style={styles.lockoutBox}>
              <Text style={styles.lockoutTitle}>Account temporarily locked</Text>
              <Text style={styles.lockoutBody}>
                Too many failed attempts. Try again in{' '}
                <Text style={styles.lockoutTimer}>
                  {lockoutMinutes}:{lockoutSeconds.toString().padStart(2, '0')}
                </Text>
              </Text>
            </View>
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
  safe: { flex: 1, backgroundColor: '#FFFFFF' },
  flex: { flex: 1 },
  scroll: { paddingHorizontal: 24, paddingTop: 48, paddingBottom: 40 },
  heading: { fontSize: 28, fontWeight: '700', color: '#111827', marginBottom: 8 },
  subheading: { fontSize: 16, color: '#6B7280', marginBottom: 32 },
  successBanner: { backgroundColor: '#D1FAE5', borderRadius: 8, padding: 14, marginBottom: 20 },
  successBannerText: { color: '#065F46', fontSize: 14, fontWeight: '500' },
  globalError: { backgroundColor: '#FEF2F2', borderRadius: 8, padding: 14, marginBottom: 20 },
  globalErrorText: { color: '#991B1B', fontSize: 14 },
  lockoutBox: {
    backgroundColor: '#FFF7ED',
    borderRadius: 8,
    padding: 16,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#FED7AA',
  },
  lockoutTitle: { fontSize: 15, fontWeight: '600', color: '#92400E', marginBottom: 6 },
  lockoutBody: { fontSize: 14, color: '#92400E' },
  lockoutTimer: { fontWeight: '700', fontVariant: ['tabular-nums'] },
  eyeButton: { paddingHorizontal: 12 },
  eyeText: { color: '#6B7280', fontSize: 14 },
  forgotRow: { alignSelf: 'flex-end', marginBottom: 24, marginTop: -8 },
  forgotText: { color: '#1A1A1A', fontSize: 14, textDecorationLine: 'underline' },
  submitButton: { marginTop: 4 },
  registerRow: { flexDirection: 'row', justifyContent: 'center', marginTop: 24 },
  registerText: { color: '#6B7280', fontSize: 14 },
  registerLink: { color: '#1A1A1A', fontSize: 14, fontWeight: '600' },
});
