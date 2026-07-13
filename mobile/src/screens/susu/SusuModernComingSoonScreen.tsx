import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { Icon, PressableScale } from '../../components/ui';
import { colors, radii, spacing } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'SusuModernComingSoon'>;

/**
 * "Modern" (fixed-term, get-your-own-savings-back) susu is a concept that
 * was explored in the redesign conversation but never committed to — only
 * Traditional (rotating-payout) susu is real. This screen exists so the
 * group-type choice is visible without pretending Modern actually works.
 */
export function SusuModernComingSoonScreen() {
  const navigation = useNavigation<Nav>();

  return (
    <SafeAreaView style={styles.safe} testID="susu-modern-coming-soon-screen">
      <View style={styles.header}>
        <TouchableOpacity
          style={styles.headerBtn}
          onPress={() => navigation.goBack()}
          accessibilityRole="button"
          accessibilityLabel="Go back"
        >
          <Text style={styles.headerBtnIcon}>←</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.body}>
        <View style={styles.iconBadge}>
          <Icon name="construct-outline" size={30} color={colors.gold.text} />
        </View>
        <Text style={styles.title}>Modern susu is coming soon</Text>
        <Text style={styles.description}>
          A fixed-term susu where you save toward your own goal, on your own schedule — no
          rotation, no waiting for your turn. We&apos;re still building this.
        </Text>

        <PressableScale
          style={styles.cta}
          onPress={() => navigation.goBack()}
          accessibilityRole="button"
          accessibilityLabel="Create a Traditional susu instead"
        >
          <Text style={styles.ctaText}>Create a Traditional susu instead</Text>
        </PressableScale>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  header: { paddingHorizontal: spacing.lg, paddingVertical: spacing.md },
  headerBtn: { width: 40, height: 40, justifyContent: 'center' },
  headerBtnIcon: { fontSize: 22, color: colors.textPrimary },
  body: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing['3xl'] },
  iconBadge: {
    width: 64,
    height: 64,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.lg,
  },
  title: { fontSize: 20, fontWeight: '700', color: colors.textPrimary, marginBottom: spacing.sm, textAlign: 'center' },
  description: {
    fontSize: 14,
    color: colors.textSecondary,
    textAlign: 'center',
    lineHeight: 21,
    marginBottom: spacing['2xl'],
  },
  cta: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingHorizontal: spacing['2xl'],
    paddingVertical: spacing.md,
  },
  ctaText: { fontSize: 15, fontWeight: '700', color: colors.neutral[900] },
});
