import React, { useEffect } from 'react';
import { View, Text, TouchableOpacity, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { CompositeNavigationProp } from '@react-navigation/native';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import type { MainTabParamList } from '../../navigation/MainTabNavigator';
import { useVaults } from '../../hooks/useVaults';
import { useWalletBalance } from '../../hooks/useWalletBalance';
import { useChallenges } from '../Challenges/useChallenges';
import { sectionFor } from '../Challenges/types';

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
  icon: string;
  title: string;
  summary: string;
  onPress: () => void;
}) {
  return (
    <TouchableOpacity
      style={styles.card}
      onPress={onPress}
      activeOpacity={0.8}
      accessibilityRole="button"
      accessibilityLabel={`${title}. ${summary}`}
    >
      <View style={styles.cardIcon}>
        <Text style={styles.cardIconText}>{icon}</Text>
      </View>
      <View style={styles.cardBody}>
        <Text style={styles.cardTitle}>{title}</Text>
        <Text style={styles.cardSummary}>{summary}</Text>
      </View>
      <Text style={styles.chevron}>›</Text>
    </TouchableOpacity>
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
          icon="🏦"
          title="Vaults"
          summary={`${vaultCount} vault${vaultCount !== 1 ? 's' : ''} · GHS ${formatCedis(vaultSavedPesewas)} saved`}
          onPress={() => navigation.navigate('VaultList')}
        />
        <DestinationCard
          icon="👛"
          title="Wallet"
          summary={
            walletBalance
              ? `GHS ${walletBalance.balanceCedis} · payouts & transfers`
              : 'Payouts & transfers'
          }
          onPress={() => navigation.navigate('Wallet')}
        />
        <DestinationCard
          icon="🏆"
          title="Challenges"
          summary={`${activeCount} active · ${availableCount} to join`}
          onPress={() => navigation.navigate('ChallengesList')}
        />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F9FAFB' },
  content: { paddingHorizontal: 20, paddingTop: 16, paddingBottom: 40 },
  heading: { fontSize: 24, fontWeight: '700', color: '#111827', marginBottom: 4 },
  subheading: { fontSize: 14, color: '#6B7280', marginBottom: 24 },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 14,
    padding: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#E5E7EB',
  },
  cardIcon: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: '#F3F4F6',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 14,
  },
  cardIconText: { fontSize: 20 },
  cardBody: { flex: 1 },
  cardTitle: { fontSize: 16, fontWeight: '700', color: '#111827', marginBottom: 2 },
  cardSummary: { fontSize: 13, color: '#6B7280' },
  chevron: { fontSize: 20, color: '#9CA3AF' },
});
