import React, { useEffect } from 'react';
import { View, Text, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { CompositeNavigationProp } from '@react-navigation/native';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import type { MainTabParamList } from '../../navigation/MainTabNavigator';
import { useVaults } from '../../hooks/useVaults';
import { useWalletBalance } from '../../hooks/useWalletBalance';
import Animated from 'react-native-reanimated';
import { useChallenges } from '../Challenges/useChallenges';
import { sectionFor } from '../Challenges/types';
import { Icon, PressableScale, fadeInUp, type IconName } from '../../components/ui';
import { colors, radii, shadows, spacing, typography } from '../../theme';

type Nav = CompositeNavigationProp<
  BottomTabNavigationProp<MainTabParamList, 'Explore'>,
  NativeStackNavigationProp<RootStackParamList>
>;

function formatCedis(pesewas: number): string {
  return (pesewas / 100).toLocaleString('en-GH', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function DestinationCard({
  icon,
  title,
  summary,
  onPress,
}: {
  icon: IconName;
  title: string;
  summary: string;
  onPress: () => void;
}) {
  return (
    <PressableScale
      style={styles.card}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${title}. ${summary}`}
    >
      <View style={styles.cardIcon}>
        <Icon name={icon} size={20} color={colors.gold.text} />
      </View>
      <View style={styles.cardBody}>
        <Text style={styles.cardTitle}>{title}</Text>
        <Text style={styles.cardSummary}>{summary}</Text>
      </View>
      <Text style={styles.chevron}>›</Text>
    </PressableScale>
  );
}

export function ExploreScreen() {
  const navigation = useNavigation<Nav>();

  const { data: vaultsData } = useVaults();
  const { balance: walletBalance, fetch: fetchWalletBalance } = useWalletBalance();
  const { data: challenges } = useChallenges();

  useEffect(() => {
    fetchWalletBalance();
  }, [fetchWalletBalance]);

  const vaultCount = vaultsData?.total_count ?? 0;
  const vaultSavedPesewas = (vaultsData?.vaults ?? []).reduce(
    (sum, v) => sum + (v.balance_pesewas ?? 0),
    0,
  );

  const activeCount = (challenges ?? []).filter(c => sectionFor(c) === 'ACTIVE').length;
  const availableCount = (challenges ?? []).filter(c => sectionFor(c) === 'AVAILABLE').length;

  return (
    <SafeAreaView style={styles.safe} testID="explore-screen">
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <Text style={styles.heading}>Explore</Text>
        <Text style={styles.subheading}>Jump straight into any part of your account.</Text>

        <DestinationCard
          icon="wallet-outline"
          title="Vaults"
          summary={`${vaultCount} vault${vaultCount !== 1 ? 's' : ''} · GHS ${formatCedis(vaultSavedPesewas)} saved`}
          onPress={() => navigation.navigate('VaultList')}
        />
        <DestinationCard
          icon="card-outline"
          title="Wallet"
          summary={
            walletBalance
              ? `GHS ${walletBalance.balanceCedis} · payouts & transfers`
              : 'Payouts & transfers'
          }
          onPress={() => navigation.navigate('Wallet')}
        />
        <DestinationCard
          icon="trophy-outline"
          title="Challenges"
          summary={`${activeCount} active · ${availableCount} to join`}
          onPress={() => navigation.navigate('ChallengesList')}
        />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  content: { paddingHorizontal: spacing.xl, paddingTop: spacing.lg, paddingBottom: spacing['4xl'] },
  heading: { ...typography.h1, color: colors.textPrimary, marginBottom: spacing.xs },
  subheading: { fontSize: 14, color: colors.textSecondary, marginBottom: spacing['2xl'] },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    padding: spacing.lg,
    marginBottom: spacing.md,
    borderWidth: 1,
    borderColor: colors.border,
    ...shadows.sm,
  },
  cardIcon: {
    width: 44,
    height: 44,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: spacing.md,
  },
  cardBody: { flex: 1 },
  cardTitle: { fontSize: 16, fontWeight: '700', color: colors.textPrimary, marginBottom: 2 },
  cardSummary: { fontSize: 13, color: colors.textSecondary },
  chevron: { fontSize: 20, color: colors.textTertiary },
});
