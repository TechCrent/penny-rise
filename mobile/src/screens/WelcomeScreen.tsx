import React, { useState } from 'react';
import { View, Text, Image, ActivityIndicator, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import NetInfo from '@react-native-community/netinfo';
import { LinearGradient } from 'expo-linear-gradient';
import Animated, { FadeIn } from 'react-native-reanimated';
import type { RootStackParamList } from '../navigation/RootNavigator';
import { useNetworkStatus } from '../hooks/useNetworkStatus';
import { Icon, PressableScale } from '../components/ui';
import type { IconName } from '../components/ui';
import { colors, radii, shadows, spacing, typography } from '../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Welcome'>;

const TRUST_POINTS: { icon: IconName; label: string }[] = [
  { icon: 'lock-closed', label: 'Bank-grade\nsecurity' },
  { icon: 'people-outline', label: 'Save with\nyour circle' },
  { icon: 'target', label: 'Hit every\ngoal' },
];

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
          <LinearGradient
            colors={[colors.gold.base, colors.gold.hover]}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 1 }}
            style={styles.logoBadgeInner}
          >
            <Image
              source={require('../../assets/images/splash-icon.png')}
              style={styles.logo}
              resizeMode="contain"
            />
          </LinearGradient>
        </Animated.View>

        <Animated.View entering={FadeIn.delay(100).duration(500)}>
          <Text style={styles.title}>PennyRise</Text>
          <Text style={styles.kicker}>SECURE · TARGET</Text>
        </Animated.View>

        <Animated.Text entering={FadeIn.delay(180).duration(500)} style={styles.tagline}>
          Save alone, save together, and actually get there.
        </Animated.Text>

        <Animated.View entering={FadeIn.delay(240).duration(500)} style={styles.trustRow}>
          {TRUST_POINTS.map(point => (
            <View key={point.label} style={styles.trustItem}>
              <View style={styles.trustIconWrap}>
                <Icon name={point.icon} size={18} color={colors.gold.text} />
              </View>
              <Text style={styles.trustLabel}>{point.label}</Text>
            </View>
          ))}
        </Animated.View>

        <View style={styles.spacer} />

        {isOffline ? (
          <View style={styles.offlineBox} testID="welcome-offline-message">
            <Text style={styles.offlineTitle}>No internet connection</Text>
            <Text style={styles.offlineBody}>
              PennyRise needs a connection to create an account or sign in. Connect to Wi-Fi or
              mobile data and try again.
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
          <Animated.View entering={FadeIn.delay(300).duration(500)} style={styles.actions}>
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
    borderRadius: radii['2xl'],
    marginBottom: spacing['2xl'],
    ...shadows.lg,
  },
  logoBadgeInner: {
    width: 116,
    height: 116,
    borderRadius: radii['2xl'],
    alignItems: 'center',
    justifyContent: 'center',
  },
  logo: { width: 66, height: 66, tintColor: colors.neutral[900] },
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
    marginBottom: spacing.xl,
  },
  tagline: {
    fontSize: 16,
    color: colors.neutral[600],
    textAlign: 'center',
    lineHeight: 23,
    paddingHorizontal: spacing.md,
    marginBottom: spacing['2xl'],
  },
  trustRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: spacing.md,
    alignSelf: 'stretch',
  },
  trustItem: { flex: 1, alignItems: 'center', gap: spacing.sm },
  trustIconWrap: {
    width: 44,
    height: 44,
    borderRadius: radii.lg,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
  },
  trustLabel: {
    fontSize: 12,
    color: colors.textSecondary,
    textAlign: 'center',
    lineHeight: 16,
    fontWeight: '500',
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
    ...shadows.md,
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
