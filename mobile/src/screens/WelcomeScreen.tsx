import React, { useState } from 'react';
import { View, Text, Image, ActivityIndicator, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import NetInfo from '@react-native-community/netinfo';
import Animated, { FadeIn } from 'react-native-reanimated';
import type { RootStackParamList } from '../navigation/RootNavigator';
import { useNetworkStatus } from '../hooks/useNetworkStatus';
import { PressableScale } from '../components/ui';
import { colors, radii, spacing, typography } from '../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Welcome'>;

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

        <Animated.View entering={FadeIn.duration(500)} style={styles.logoBadge}>
          <Image
            source={require('../../assets/images/splash-icon.png')}
            style={styles.logo}
            resizeMode="contain"
          />
        </Animated.View>

        <Animated.View entering={FadeIn.delay(100).duration(500)}>
          <Text style={styles.title}>Stash</Text>
          <Text style={styles.kicker}>SECURE · TARGET</Text>
        </Animated.View>

        <Animated.Text entering={FadeIn.delay(180).duration(500)} style={styles.tagline}>
          Save alone, save together, and actually get there.
        </Animated.Text>

        <View style={styles.spacer} />

        {isOffline ? (
          <View style={styles.offlineBox} testID="welcome-offline-message">
            <Text style={styles.offlineTitle}>No internet connection</Text>
            <Text style={styles.offlineBody}>
              Stash needs a connection to create an account or sign in. Connect to Wi-Fi or mobile
              data and try again.
            </Text>
            <PressableScale
              style={styles.offlineRetryButton}
              onPress={recheckConnection}
              disabled={checking}
              testID="welcome-offline-retry"
            >
              {checking ? (
                <ActivityIndicator color={colors.status.errorText} size="small" />
              ) : (
                <Text style={styles.offlineRetryText}>Try again</Text>
              )}
            </PressableScale>
          </View>
        ) : (
          <Animated.View entering={FadeIn.delay(260).duration(500)} style={styles.actions}>
            <PressableScale
              style={styles.primaryButton}
              onPress={() => navigation.navigate('Register')}
              accessibilityRole="button"
            >
              <Text style={styles.primaryButtonText}>Create account</Text>
            </PressableScale>

            <PressableScale
              style={styles.secondaryButton}
              onPress={() => navigation.navigate('Login')}
              accessibilityRole="button"
            >
              <Text style={styles.secondaryButtonText}>I already have one</Text>
            </PressableScale>
          </Animated.View>
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
  safe: { flex: 1, backgroundColor: colors.background },
  content: { flex: 1, paddingHorizontal: 28, paddingBottom: spacing.lg, alignItems: 'center' },
  spacer: { flex: 1 },
  logoBadge: {
    width: 120,
    height: 120,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing['2xl'],
  },
  logo: { width: 72, height: 72, tintColor: colors.gold.text },
  title: {
    ...typography.display,
    color: colors.textPrimary,
    textAlign: 'center',
    marginBottom: spacing.xs,
  },
  kicker: {
    ...typography.label,
    color: colors.gold.text,
    textAlign: 'center',
    letterSpacing: 3,
    marginBottom: spacing['2xl'],
  },
  tagline: {
    fontSize: 16,
    color: colors.neutral[600],
    textAlign: 'center',
    lineHeight: 23,
    paddingHorizontal: spacing.md,
  },
  actions: { alignSelf: 'stretch' },
  primaryButton: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingVertical: 16,
    alignItems: 'center',
    justifyContent: 'center',
    alignSelf: 'stretch',
    marginBottom: spacing.md,
  },
  primaryButtonText: { ...typography.button, color: colors.neutral[900] },
  secondaryButton: {
    borderWidth: 1.5,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    borderRadius: radii.md,
    paddingVertical: 16,
    alignItems: 'center',
    justifyContent: 'center',
    alignSelf: 'stretch',
    marginBottom: spacing.xl,
  },
  secondaryButtonText: { ...typography.button, color: colors.textPrimary },
  offlineBox: {
    backgroundColor: colors.status.errorBg,
    borderWidth: 1,
    borderColor: '#FECACA',
    borderRadius: radii.lg,
    padding: spacing.lg,
    alignSelf: 'stretch',
    alignItems: 'center',
    marginBottom: spacing.xl,
  },
  offlineTitle: {
    color: colors.status.errorText,
    fontSize: 15,
    fontWeight: '700',
    marginBottom: spacing.sm,
  },
  offlineBody: {
    color: colors.status.errorText,
    fontSize: 13,
    textAlign: 'center',
    lineHeight: 18,
    marginBottom: spacing.md,
  },
  offlineRetryButton: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.status.errorBorder,
    borderRadius: radii.sm,
    paddingVertical: 10,
    paddingHorizontal: spacing.xl,
    minWidth: 100,
    alignItems: 'center',
  },
  offlineRetryText: { color: colors.status.errorText, fontSize: 13, fontWeight: '700' },
  legal: {
    fontSize: 12,
    color: colors.textTertiary,
    textAlign: 'center',
    lineHeight: 18,
  },
  legalLink: { color: colors.textSecondary, textDecorationLine: 'underline' },
});
