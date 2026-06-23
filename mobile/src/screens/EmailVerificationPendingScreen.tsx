import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { RootStackParamList } from '../navigation/RootNavigator';
import { apiClient } from '../api/client';

type EmailVerificationRouteProp = RouteProp<RootStackParamList, 'EmailVerificationPending'>;

export default function EmailVerificationPendingScreen() {
  const route = useRoute<EmailVerificationRouteProp>();
  const { email } = route.params;

  const [resendState, setResendState] = useState<'idle' | 'loading' | 'sent' | 'error'>('idle');

  const handleResend = async () => {
    setResendState('loading');
    try {
      await apiClient.post('/api/v1/auth/resend-verification', { email });
      setResendState('sent');
    } catch {
      setResendState('error');
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.container}>
        <Text style={styles.emoji}>✉️</Text>
        <Text style={styles.heading}>Check your inbox</Text>
        <Text style={styles.body}>
          We sent a verification link to{' '}
          <Text style={styles.email}>{email}</Text>.
          {'\n\n'}
          Click the link in the email to activate your account.
        </Text>

        {resendState === 'sent' ? (
          <Text style={styles.sentText}>Resent! Check your inbox again.</Text>
        ) : resendState === 'error' ? (
          <Text style={styles.errorText}>
            Couldn&apos;t resend right now. Please try again.
          </Text>
        ) : (
          <TouchableOpacity
            onPress={handleResend}
            disabled={resendState === 'loading'}
            style={styles.resendButton}
          >
            {resendState === 'loading' ? (
              <ActivityIndicator size="small" color="#1A1A1A" />
            ) : (
              <Text style={styles.resendText}>Resend verification email</Text>
            )}
          </TouchableOpacity>
        )}

        <Text style={styles.hint}>
          Didn&apos;t get it? Check your spam folder, or tap the button above.
        </Text>
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
  resendButton: { padding: 12 },
  resendText: { color: '#1A1A1A', fontSize: 15, textDecorationLine: 'underline' },
  sentText: { color: '#059669', fontSize: 15, fontWeight: '600', marginBottom: 16 },
  errorText: { color: '#EF4444', fontSize: 15, marginBottom: 16 },
  hint: { fontSize: 13, color: '#9CA3AF', textAlign: 'center', marginTop: 24 },
});
