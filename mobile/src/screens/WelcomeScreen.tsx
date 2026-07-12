import React, { useState } from 'react';
import { View, Text, TouchableOpacity, Image, ActivityIndicator, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import NetInfo from '@react-native-community/netinfo';
import type { RootStackParamList } from '../navigation/RootNavigator';
import { useNetworkStatus } from '../hooks/useNetworkStatus';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Welcome'>;

const INDIGO = '#4F46E5';

export default function WelcomeScreen() {
  const navigation = useNavigation<Nav>();
  const { isOffline } = useNetworkStatus();
  const [checking, setChecking] = useState(false);

  const recheckConnection = async () => {
    setChecking(true);
    await NetInfo.fetch();
    setChecking(false);
  };

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.content}>
        <View style={styles.spacer} />

        <View style={styles.logoBadge}>
          <Image
            source={require('../../assets/images/splash-icon.png')}
            style={styles.logo}
            resizeMode="contain"
          />
        </View>

        <Text style={styles.title}>Stash</Text>
        <Text style={styles.kicker}>SECURE · TARGET</Text>

        <Text style={styles.tagline}>Save alone, save together, and actually get there.</Text>

        <View style={styles.spacer} />

        {isOffline ? (
          <View style={styles.offlineBox} testID="welcome-offline-message">
            <Text style={styles.offlineTitle}>No internet connection</Text>
            <Text style={styles.offlineBody}>
              Stash needs a connection to create an account or sign in. Connect to Wi-Fi or mobile
              data and try again.
            </Text>
            <TouchableOpacity
              style={styles.offlineRetryButton}
              onPress={recheckConnection}
              disabled={checking}
              testID="welcome-offline-retry"
            >
              {checking ? (
                <ActivityIndicator color="#991B1B" size="small" />
              ) : (
                <Text style={styles.offlineRetryText}>Try again</Text>
              )}
            </TouchableOpacity>
          </View>
        ) : (
          <>
            <TouchableOpacity
              style={styles.primaryButton}
              onPress={() => navigation.navigate('Register')}
              activeOpacity={0.85}
              accessibilityRole="button"
            >
              <Text style={styles.primaryButtonText}>Create account</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={styles.secondaryButton}
              onPress={() => navigation.navigate('Login')}
              activeOpacity={0.7}
              accessibilityRole="button"
            >
              <Text style={styles.secondaryButtonText}>I already have one</Text>
            </TouchableOpacity>
          </>
        )}

        <Text style={styles.legal}>
          By continuing you agree to our{' '}
          <Text style={styles.legalLink} onPress={() => navigation.navigate('Legal')}>
            Terms of Service and Privacy Policy
          </Text>
          .
        </Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#FFFFFF' },
  content: { flex: 1, paddingHorizontal: 28, paddingBottom: 16, alignItems: 'center' },
  spacer: { flex: 1 },
  logoBadge: {
    width: 120,
    height: 120,
    borderRadius: 60,
    backgroundColor: '#EEF2FF',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 24,
  },
  logo: { width: 72, height: 72, tintColor: INDIGO },
  title: { fontSize: 34, fontWeight: '800', color: '#111827', marginBottom: 6 },
  kicker: {
    fontSize: 12,
    fontWeight: '700',
    color: INDIGO,
    letterSpacing: 3,
    marginBottom: 24,
  },
  tagline: {
    fontSize: 16,
    color: '#4B5563',
    textAlign: 'center',
    lineHeight: 23,
    paddingHorizontal: 12,
  },
  primaryButton: {
    backgroundColor: INDIGO,
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
    justifyContent: 'center',
    alignSelf: 'stretch',
    marginBottom: 12,
  },
  primaryButtonText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
  secondaryButton: {
    borderWidth: 1.5,
    borderColor: '#C7D2FE',
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
    justifyContent: 'center',
    alignSelf: 'stretch',
    marginBottom: 20,
  },
  secondaryButtonText: { color: INDIGO, fontSize: 16, fontWeight: '700' },
  offlineBox: {
    backgroundColor: '#FEF2F2',
    borderWidth: 1,
    borderColor: '#FECACA',
    borderRadius: 12,
    padding: 16,
    alignSelf: 'stretch',
    alignItems: 'center',
    marginBottom: 20,
  },
  offlineTitle: { color: '#991B1B', fontSize: 15, fontWeight: '700', marginBottom: 6 },
  offlineBody: {
    color: '#991B1B',
    fontSize: 13,
    textAlign: 'center',
    lineHeight: 18,
    marginBottom: 12,
  },
  offlineRetryButton: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#FCA5A5',
    borderRadius: 8,
    paddingVertical: 10,
    paddingHorizontal: 20,
    minWidth: 100,
    alignItems: 'center',
  },
  offlineRetryText: { color: '#991B1B', fontSize: 13, fontWeight: '700' },
  legal: {
    fontSize: 12,
    color: '#9CA3AF',
    textAlign: 'center',
    lineHeight: 18,
  },
  legalLink: { color: '#6B7280', textDecorationLine: 'underline' },
});
