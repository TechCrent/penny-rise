import React from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { colors, radii, spacing, typography } from '../../theme';

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
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} hitSlop={8}>
          <Text style={styles.backText}>← Back</Text>
        </TouchableOpacity>
        <Text style={styles.title}>Terms &amp; Privacy</Text>
        <View style={styles.headerSpacer} />
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.placeholderBanner}>
          <Text style={styles.placeholderText}>
            Placeholder copy — pending final review from legal. This is not the binding Terms of
            Service or Privacy Policy.
          </Text>
        </View>

        <Text style={styles.sectionHeading}>Terms of Service</Text>
        <Text style={styles.body}>
          By using Stash, you agree to save responsibly, keep your account credentials secure, and
          provide accurate information during signup and identity verification. Stash reserves the
          right to suspend accounts that violate these terms or applicable law.
        </Text>

        <Text style={styles.sectionHeading}>Privacy Policy</Text>
        <Text style={styles.body}>
          Stash collects the information you provide at signup, KYC verification documents, and
          transaction data needed to operate your vaults, susu groups, and transfers. We do not sell
          your personal data. Data is retained as required for regulatory and operational purposes.
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.sm,
    paddingBottom: spacing.lg,
  },
  backText: { color: colors.textPrimary, fontSize: 15 },
  title: { ...typography.h3, color: colors.textPrimary },
  headerSpacer: { width: 44 },
  content: { paddingHorizontal: spacing.xl, paddingBottom: spacing['4xl'] },
  placeholderBanner: {
    backgroundColor: colors.status.warningBg,
    borderRadius: radii.md,
    padding: spacing.md,
    marginBottom: spacing['2xl'],
  },
  placeholderText: { color: colors.status.warningText, fontSize: 13, lineHeight: 19 },
  sectionHeading: {
    fontSize: 18,
    fontWeight: '700',
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    marginTop: spacing.sm,
  },
  body: { fontSize: 14, color: colors.neutral[700], lineHeight: 21, marginBottom: spacing.xl },
});
