import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import Animated from 'react-native-reanimated';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { PrimaryButton } from '../../components/PrimaryButton';
import { Icon, ScreenHeader, fadeInUp } from '../../components/ui';
import { colors, radii, shadows, spacing, typography } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'WalletWithdrawComingSoon'>;

/**
 * Only vaults support withdrawal today (VaultWithdrawalService) — there is
 * no wallet-to-MoMo payout flow on the backend yet. This placeholder keeps
 * the Withdraw button visible on the Wallet screen without pretending the
 * feature works, mirroring SusuModernComingSoonScreen's pattern.
 */
export function WalletWithdrawComingSoonScreen() {
  const navigation = useNavigation<Nav>();

  return (
    <SafeAreaView style={styles.safe} testID="wallet-withdraw-coming-soon-screen">
      <View style={styles.header}>
        <ScreenHeader onBack={() => navigation.goBack()} />
      </View>

      <View style={styles.body}>
        <Animated.View entering={fadeInUp(60)} style={styles.card}>
          <View style={styles.iconBadge}>
            <Icon name="construct-outline" size={34} color={colors.gold.text} />
          </View>
          <Text style={styles.title}>Wallet withdrawals are coming soon</Text>
          <Text style={styles.description}>
            Moving wallet balance out to MoMo isn&apos;t available yet. In the meantime you can
            withdraw from a vault instead.
          </Text>

          <PrimaryButton
            title="Got it"
            onPress={() => navigation.goBack()}
            accessibilityLabel="Go back"
            style={styles.cta}
          />
        </Animated.View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  header: { paddingHorizontal: spacing.xl, paddingTop: spacing.md },
  body: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: spacing.xl,
  },
  card: {
    width: '100%',
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: spacing['4xl'],
    paddingHorizontal: spacing.xl,
    ...shadows.sm,
  },
  iconBadge: {
    width: 80,
    height: 80,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.xl,
  },
  title: {
    ...typography.h2,
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    textAlign: 'center',
  },
  description: {
    ...typography.body,
    color: colors.textSecondary,
    textAlign: 'center',
    marginBottom: spacing['2xl'],
  },
  cta: { alignSelf: 'stretch' },
});
