import React, { useState, useEffect, useRef } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../navigation/RootNavigator';
import { resendVerification } from '../api/auth';

type Nav = NativeStackNavigationProp<RootStackParamList, 'EmailVerificationPending'>;
type Route = RouteProp<RootStackParamList, 'EmailVerificationPending'>;

const RESEND_COOLDOWN_SECONDS = 60;

export default function EmailVerificationPendingScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const { email } = route.params;

  const [resendState, setResendState] = useState<'idle' | 'loading' | 'sent' | 'error'>('idle');
  const [cooldownRemaining, setCooldown] = useState(0);
  const cooldownRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(
    () => () => {
      if (cooldownRef.current) clearInterval(cooldownRef.current);
    },
    [],
  );

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
    if (resendState === 'loading' || cooldownRemaining > 0) return;
    setResendState('loading');
    try {
      await resendVerification(email);
      setResendState('sent');
      startCooldown();
    } catch {
      setResendState('error');
    }
  };

  const canResend = resendState !== 'loading' && cooldownRemaining === 0;

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.container}>
        <Text style={styles.emoji}>✉️</Text>
        <Text style={styles.heading}>Check your inbox</Text>
        <Text style={styles.body}>
          We sent a verification link to <Text style={styles.email}>{email}</Text>.{'\n\n'}
          Click the link in the email to activate your account. It expires in 24 hours.
        </Text>

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
            <ActivityIndicator size="small" color="#1A1A1A" />
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
  safe: { flex: 1, backgroundColor: '#FFFFFF' },
  container: { flex: 1, paddingHorizontal: 24, paddingTop: 80, alignItems: 'center' },
  emoji: { fontSize: 56, marginBottom: 24 },
  heading: {
    fontSize: 26,
    fontWeight: '700',
    color: '#111827',
    marginBottom: 16,
    textAlign: 'center',
  },
  body: {
    fontSize: 16,
    color: '#6B7280',
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: 32,
  },
  email: { fontWeight: '600', color: '#111827' },
  sentText: { color: '#059669', fontSize: 14, fontWeight: '600', marginBottom: 12 },
  errorText: { color: '#EF4444', fontSize: 14, marginBottom: 12 },
  resendButton: { paddingVertical: 12, paddingHorizontal: 16 },
  resendDisabled: { opacity: 0.5 },
  resendText: { color: '#1A1A1A', fontSize: 15, textDecorationLine: 'underline' },
  resendTextDisabled: { textDecorationLine: 'none' },
  hint: { fontSize: 13, color: '#9CA3AF', textAlign: 'center', marginTop: 24 },
  loginLink: { marginTop: 32, padding: 12 },
  loginLinkText: { color: '#6B7280', fontSize: 14 },
});
