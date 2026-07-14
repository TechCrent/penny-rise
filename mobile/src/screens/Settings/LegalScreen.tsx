import React from 'react';
import { View, Text, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import Animated from 'react-native-reanimated';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { Banner, GradientHero, ScreenHeader, fadeInUp } from '../../components/ui';
import { colors, radii, spacing, typography, shadows } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Legal'>;

/**
 * Placeholder Terms of Service / Privacy Policy copy — no legal content
 * exists anywhere in the app yet. This exists so the signup consent
 * checkbox and Settings' "Legal" row have somewhere to link to; replace
 * with real copy once legal has signed off.
 */
export function LegalScreen() {
  const navigation = useNavigation<Nav>();

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView
        contentContainerStyle={styles.scroll}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        <ScreenHeader title="Terms & Privacy" onBack={() => navigation.goBack()} />

        <Animated.View entering={fadeInUp(50)}>
          <GradientHero icon="receipt-outline" title="Terms & Privacy" />
        </Animated.View>

        <Animated.View entering={fadeInUp(110)}>
          <Banner
            tone="warning"
            message="Placeholder copy — pending final review from legal. This is not the binding Terms of Service or Privacy Policy."
          />
        </Animated.View>

        <Animated.View entering={fadeInUp(170)} style={styles.contentCard}>
          <Text style={styles.sectionHeading}>Terms of Service</Text>
          <Text style={styles.body}>
            By using PennyRise, you agree to save responsibly, keep your account credentials secure,
            and provide accurate information during signup and identity verification. PennyRise
            reserves the right to suspend accounts that violate these terms or applicable law.
          </Text>

          <Text style={styles.sectionHeading}>Privacy Policy</Text>
          <Text style={styles.body}>
            PennyRise collects the information you provide at signup, KYC verification documents,
            and transaction data needed to operate your vaults, susu groups, and transfers. We do
            not sell your personal data. Data is retained as required for regulatory and operational
            purposes.
          </Text>
        </Animated.View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  scroll: {
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.md,
    paddingBottom: spacing['4xl'],
  },
  contentCard: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
    marginTop: spacing.xl,
    ...shadows.sm,
  },
  sectionHeading: {
    ...typography.h3,
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    marginTop: spacing.sm,
  },
  body: {
    fontSize: 15,
    color: colors.textSecondary,
    lineHeight: 24,
    marginBottom: spacing.xl,
  },
});
