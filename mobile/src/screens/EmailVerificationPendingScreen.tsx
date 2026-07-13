import React, { useState, useEffect, useRef } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Ionicons } from '@expo/vector-icons';
import type { RootStackParamList } from '../navigation/RootNavigator';
import { resendVerification, verifyEmail } from '../api/auth';
import { extractApiError, apiClient } from '../api/client';
import { colors, radii, spacing, typography } from '../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'EmailVerificationPending'>;
type Route = RouteProp<RootStackParamList, 'EmailVerificationPending'>;

const RESEND_COOLDOWN_SECONDS = 60;

export default function EmailVerificationPendingScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const email = route.params?.email ?? '';
  const verifyToken = route.params?.token;

  const [verifyState, setVerifyState] = useState<'idle' | 'loading' | 'success' | 'error'>('idle');
  const [verifyError, setVerifyError] = useState<string | null>(null);
  const [resendState, setResendState] = useState<'idle' | 'loading' | 'sent' | 'error'>('idle');
  const [cooldownRemaining, setCooldown] = useState(0);
  const cooldownRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const verifyAttemptedRef = useRef(false);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(
    () => () => {
      if (cooldownRef.current) clearInterval(cooldownRef.current);
      if (pollRef.current) clearInterval(pollRef.current);
    },
    [],
  );

  // Poll every 3 s while the user is on this screen so that clicking the link
  // in Mailpit (on a laptop) automatically redirects the phone to sign-in.
  useEffect(() => {
    if (!email || verifyToken) return; // token path handles itself; no email = nothing to poll
    pollRef.current = setInterval(async () => {
      try {
        const { data } = await apiClient.get<{ verified: boolean }>(
          `/api/v1/auth/email-verified?email=${encodeURIComponent(email)}`,
        );
        if (data.verified) {
          clearInterval(pollRef.current!);
          navigation.reset({
            index: 0,
            routes: [
              { name: 'Login', params: { successBanner: 'Email verified! You can sign in now.' } },
            ],
          });
        }
      } catch {
        // Silently ignore — poll will retry next tick
      }
    }, 3000);
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, [email, verifyToken, navigation]);

  useEffect(() => {
    if (!verifyToken || verifyAttemptedRef.current) return;
    verifyAttemptedRef.current = true;

    (async () => {
      setVerifyState('loading');
      setVerifyError(null);
      try {
        await verifyEmail(verifyToken);
        setVerifyState('success');
        navigation.reset({
          index: 0,
          routes: [
            {
              name: 'Login',
              params: { successBanner: 'Email verified! You can sign in now.' },
            },
          ],
        });
      } catch (error) {
        console.error(error);
        const apiError = extractApiError(error);

        if (apiError?.code === 'AUTH_VERIFICATION_TOKEN_ALREADY_USED') {
          // The token was already consumed by an earlier hit on this same link (e.g. a
          // duplicate deep-link delivery or a mail-client link scanner) — the email is
          // still genuinely verified, so treat this the same as a fresh success.
          setVerifyState('success');
          navigation.reset({
            index: 0,
            routes: [
              {
                name: 'Login',
                params: { successBanner: 'Email verified! You can sign in now.' },
              },
            ],
          });
          return;
        }

        setVerifyState('error');
        setVerifyError(apiError?.message ?? 'Verification link is invalid or has expired.');
      }
    })();
  }, [verifyToken, navigation]);

  const startCooldown = () => {
    setCooldown(RESEND_COOLDOWN_SECONDS);
    cooldownRef.current = setInterval(() => {
      setCooldown(s => {
        if (s <= 1) {
          clearInterval(cooldownRef.current!);
          return 0;
        }
        return s - 1;
      });
    }, 1000);
  };

  const handleResend = async () => {
    if (!email || resendState === 'loading' || cooldownRemaining > 0) return;
    setResendState('loading');
    try {
      await resendVerification(email);
      setResendState('sent');
      startCooldown();
    } catch (err) {
      console.error(err);
      setResendState('error');
    }
  };

  const canResend = !!email && resendState !== 'loading' && cooldownRemaining === 0;

  if (verifyState === 'loading') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={[styles.container, styles.centered]}>
          <ActivityIndicator size="large" color={colors.gold.base} />
          <Text style={styles.verifyingText}>Verifying your email…</Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.container}>
        <View style={styles.iconBadge}>
          <Ionicons name="mail-outline" size={32} color={colors.gold.text} />
        </View>
        <Text style={styles.heading}>Check your inbox</Text>
        <Text style={styles.body}>
          {email ? (
            <>
              We sent a verification link to <Text style={styles.email}>{email}</Text>.
            </>
          ) : (
            <>We sent a verification link to your email address.</>
          )}
          {'\n\n'}
          Click the link in the email to activate your account. It expires in 24 hours.
        </Text>

        {verifyState === 'error' && verifyError ? (
          <Text style={styles.errorText}>{verifyError}</Text>
        ) : null}

        {resendState === 'sent' ? (
          <Text style={styles.sentText}>Verification email resent!</Text>
        ) : resendState === 'error' ? (
          <Text style={styles.errorText}>Couldn&apos;t resend — please try again shortly.</Text>
        ) : null}

        <TouchableOpacity
          onPress={handleResend}
          disabled={!canResend}
          style={[styles.resendButton, !canResend ? styles.resendDisabled : null]}
        >
          {resendState === 'loading' ? (
            <ActivityIndicator size="small" color={colors.textPrimary} />
          ) : (
            <Text style={[styles.resendText, !canResend ? styles.resendTextDisabled : null]}>
              {cooldownRemaining > 0
                ? `Resend available in ${cooldownRemaining}s`
                : 'Resend verification email'}
            </Text>
          )}
        </TouchableOpacity>

        <Text style={styles.hint}>Didn&apos;t get it? Check your spam folder first.</Text>

        <TouchableOpacity onPress={() => navigation.navigate('Login')} style={styles.loginLink}>
          <Text style={styles.loginLinkText}>Back to sign in</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  container: { flex: 1, paddingHorizontal: spacing.xl, paddingTop: 80, alignItems: 'center' },
  centered: { justifyContent: 'center' },
  verifyingText: { marginTop: spacing.lg, fontSize: 16, color: colors.textSecondary },
  iconBadge: {
    width: 72,
    height: 72,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing['2xl'],
  },
  heading: {
    ...typography.h1,
    color: colors.textPrimary,
    marginBottom: spacing.lg,
    textAlign: 'center',
  },
  body: {
    fontSize: 16,
    color: colors.textSecondary,
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: spacing['3xl'],
  },
  email: { fontWeight: '600', color: colors.textPrimary },
  sentText: { color: colors.status.success, fontSize: 14, fontWeight: '600', marginBottom: spacing.md },
  errorText: { color: colors.status.error, fontSize: 14, marginBottom: spacing.md },
  resendButton: { paddingVertical: spacing.md, paddingHorizontal: spacing.lg },
  resendDisabled: { opacity: 0.5 },
  resendText: { color: colors.textPrimary, fontSize: 15, textDecorationLine: 'underline' },
  resendTextDisabled: { textDecorationLine: 'none' },
  hint: { fontSize: 13, color: colors.textTertiary, textAlign: 'center', marginTop: spacing.xl },
  loginLink: { marginTop: spacing['3xl'], padding: spacing.md },
  loginLinkText: { color: colors.textSecondary, fontSize: 14 },
});
