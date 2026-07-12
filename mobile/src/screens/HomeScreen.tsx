import React, { useCallback, useEffect, useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  RefreshControl,
  TouchableOpacity,
  StyleSheet,
  Platform,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { CompositeNavigationProp } from '@react-navigation/native';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import type { RootStackParamList } from '../navigation/RootNavigator';
import type { MainTabParamList } from '../navigation/MainTabNavigator';
import { useAuth } from '../auth/AuthContext';
import { useKycResumability } from '../hooks/useKycResumability';
import { useVaults } from '../hooks/useVaults';
import { useWalletBalance } from '../hooks/useWalletBalance';
import { useSusuGroups } from '../hooks/useSusuGroups';
import { useUnreadNotificationsCount } from '../features/notifications/useNotificationsList';
import { fetchUnifiedTransactions } from '../api/transactionHistoryApi';
import { useChallenges } from './Challenges/useChallenges';
import { sectionFor } from './Challenges/types';
import type { Challenge } from './Challenges/types';
import { ChallengeCard } from './Challenges/components/ChallengeCard';
import { VaultCard } from '../components/VaultCard';
import { SusuCard } from '../components/susu/SusuCard';
import { HomeSkeleton } from '../components/HomeSkeleton';
import type { VaultListItem } from '../api/vaults';
import type { UnifiedTransactionItem } from './TransactionHistory/types';

type Nav = CompositeNavigationProp<
  BottomTabNavigationProp<MainTabParamList, 'Home'>,
  NativeStackNavigationProp<RootStackParamList>
>;

type HomeState = 'TOTAL' | 'SAVINGS' | 'WALLET';

const SEGMENTS: { id: HomeState; label: string }[] = [
  { id: 'TOTAL', label: 'Total' },
  { id: 'SAVINGS', label: 'Savings' },
  { id: 'WALLET', label: 'Wallet' },
];

function computeTotalPesewas(vaults: VaultListItem[]): number | null {
  const available = vaults.filter(v => v.balance_pesewas !== null);
  if (available.length === 0) return null;
  return available.reduce((sum, v) => sum + (v.balance_pesewas ?? 0), 0);
}

function formatCedis(pesewas: number): string {
  return (pesewas / 100).toLocaleString('en-GH', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}

export default function HomeScreen() {
  const navigation = useNavigation<Nav>();
  const { kycStatus } = useAuth();
  useKycResumability({ enabled: kycStatus !== 'APPROVED' });

  const [homeState, setHomeState] = useState<HomeState>('TOTAL');

  // Bottom-nav "Home" always resets to Total state — whether re-pressed while
  // already on Home, or pressed to switch back from Explore/Profile.
  useEffect(() => {
    const unsubscribe = navigation.addListener('tabPress', () => {
      setHomeState('TOTAL');
    });
    return unsubscribe;
  }, [navigation]);

  const {
    data: vaultsData,
    isLoading: vaultsLoading,
    isFetching,
    error: vaultsError,
    refetch: refetchVaults,
  } = useVaults();
  const { balance: walletBalance, loading: walletLoading, fetch: fetchWalletBalance } =
    useWalletBalance();
  const { groups: susuGroups, loading: susuLoading, fetch: fetchSusuGroups } = useSusuGroups();
  const { data: challenges } = useChallenges();
  const unreadNotificationsCount = useUnreadNotificationsCount();

  useEffect(() => {
    fetchWalletBalance();
    fetchSusuGroups();
  }, [fetchWalletBalance, fetchSusuGroups]);

  const onRefresh = useCallback(() => {
    refetchVaults();
    fetchWalletBalance();
    fetchSusuGroups();
  }, [refetchVaults, fetchWalletBalance, fetchSusuGroups]);

  const activeVaults = (vaultsData?.vaults ?? []).filter(
    v => v.status === 'ACTIVE' || v.status === 'EARLY_EXIT_PENDING',
  );
  const vaultTotalPesewas = vaultsData ? computeTotalPesewas(vaultsData.vaults) : null;
  const walletPesewas = walletBalance?.balancePesewas ?? 0;
  const grandTotalPesewas =
    vaultTotalPesewas !== null ? vaultTotalPesewas + walletPesewas : null;

  const activeChallenges = (challenges ?? []).filter(c => sectionFor(c) === 'ACTIVE');

  if (vaultsLoading) {
    return (
      <SafeAreaView style={styles.safe}>
        <HomeSkeleton />
      </SafeAreaView>
    );
  }

  const heroValue =
    homeState === 'TOTAL' ? grandTotalPesewas : homeState === 'SAVINGS' ? vaultTotalPesewas : walletPesewas;
  const heroLabel =
    homeState === 'TOTAL' ? 'Total balance' : homeState === 'SAVINGS' ? 'Savings total' : 'Wallet balance';

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl refreshing={isFetching && !vaultsLoading} onRefresh={onRefresh} />
        }
      >
        <View style={styles.header}>
          <View>
            <Text style={styles.greeting}>{greeting()}</Text>
            <Text style={styles.subhead}>Here&apos;s how your money is doing</Text>
          </View>
          <View style={styles.headerButtons}>
            <TouchableOpacity
              style={styles.bellButton}
              onPress={() => navigation.navigate('Notifications')}
              accessibilityRole="button"
              accessibilityLabel="Notifications"
            >
              <Text style={styles.bellIcon}>🔔</Text>
              {unreadNotificationsCount > 0 && (
                <View style={styles.bellBadge}>
                  <Text style={styles.bellBadgeText}>
                    {unreadNotificationsCount > 9 ? '9+' : unreadNotificationsCount}
                  </Text>
                </View>
              )}
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.segmentRow} testID="home-state-switcher">
          {SEGMENTS.map(seg => (
            <TouchableOpacity
              key={seg.id}
              style={[styles.segment, homeState === seg.id && styles.segmentActive]}
              onPress={() => setHomeState(seg.id)}
              accessibilityRole="tab"
              accessibilityState={{ selected: homeState === seg.id }}
              accessibilityLabel={`${seg.label} state`}
            >
              <Text style={[styles.segmentText, homeState === seg.id && styles.segmentTextActive]}>
                {seg.label}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        <View style={styles.heroCard}>
          <Text style={styles.heroLabel}>{heroLabel}</Text>
          {heroValue === null ? (
            <Text style={styles.heroBalanceUnavailable}>
              {vaultsError ? 'Could not load balances' : '—'}
            </Text>
          ) : (
            <View style={styles.heroBalanceRow}>
              <Text style={styles.heroCurrency}>GHS </Text>
              <Text style={styles.heroBalance}>{formatCedis(heroValue)}</Text>
            </View>
          )}
          {homeState === 'TOTAL' && (
            <Text style={styles.heroSub}>
              Across {vaultsData?.total_count ?? 0} vault
              {(vaultsData?.total_count ?? 0) !== 1 ? 's' : ''} + wallet
            </Text>
          )}
        </View>

        {homeState === 'TOTAL' && (
          <TotalStateBody
            navigation={navigation}
            hasVaults={activeVaults.length > 0}
            vaultCount={activeVaults.length}
            susuCount={susuGroups.filter(g => g.status === 'ACTIVE').length}
            activeChallenges={activeChallenges}
          />
        )}

        {homeState === 'SAVINGS' && (
          <SavingsStateBody
            navigation={navigation}
            vaults={activeVaults}
            vaultsLoading={vaultsLoading}
          />
        )}

        {homeState === 'WALLET' && (
          <WalletStateBody
            navigation={navigation}
            susuGroups={susuGroups.filter(g => g.status === 'ACTIVE')}
            susuLoading={susuLoading}
            walletLoading={walletLoading}
          />
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Total state body
// ─────────────────────────────────────────────────────────────────────────────

function TotalStateBody({
  navigation,
  hasVaults,
  vaultCount,
  susuCount,
  activeChallenges,
}: {
  navigation: Nav;
  hasVaults: boolean;
  vaultCount: number;
  susuCount: number;
  activeChallenges: Challenge[];
}) {
  return (
    <>
      <View style={styles.actionRow}>
        <TouchableOpacity
          style={styles.actionTile}
          onPress={() =>
            hasVaults
              ? navigation.navigate('AccountPicker', { mode: 'DEPOSIT' })
              : navigation.navigate('CreateVault')
          }
          accessibilityRole="button"
          accessibilityLabel="Deposit"
        >
          <Text style={styles.actionIcon}>↓</Text>
          <Text style={styles.actionLabel}>Deposit</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.actionTile}
          onPress={() => navigation.navigate('RecipientPicker')}
          accessibilityRole="button"
          accessibilityLabel="Send"
        >
          <Text style={styles.actionIcon}>→</Text>
          <Text style={styles.actionLabel}>Send</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.actionTile}
          onPress={() =>
            hasVaults
              ? navigation.navigate('AccountPicker', { mode: 'WITHDRAW' })
              : navigation.navigate('CreateVault')
          }
          accessibilityRole="button"
          accessibilityLabel="Withdraw"
        >
          <Text style={styles.actionIcon}>↑</Text>
          <Text style={styles.actionLabel}>Withdraw</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.summaryRow} testID="portfolio-summary">
        <View style={styles.summaryTile}>
          <Text style={styles.summaryValue}>{vaultCount}</Text>
          <Text style={styles.summaryLabel}>vault{vaultCount !== 1 ? 's' : ''}</Text>
        </View>
        <View style={styles.summaryTile}>
          <Text style={styles.summaryValue}>{susuCount}</Text>
          <Text style={styles.summaryLabel}>susu group{susuCount !== 1 ? 's' : ''}</Text>
        </View>
      </View>

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Challenges</Text>
        <TouchableOpacity onPress={() => navigation.navigate('ChallengesList')}>
          <Text style={styles.seeAll}>View all</Text>
        </TouchableOpacity>
      </View>
      {activeChallenges.length > 0 ? (
        <View style={styles.negateInset}>
          {activeChallenges.slice(0, 3).map(challenge => (
            <ChallengeCard
              key={challenge.id}
              challenge={challenge}
              onPress={c => navigation.navigate('ChallengeDetail', { challengeId: c.id })}
            />
          ))}
        </View>
      ) : (
        <Text style={styles.emptySectionText}>No active challenges yet.</Text>
      )}
    </>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Savings state body
// ─────────────────────────────────────────────────────────────────────────────

function SavingsStateBody({
  navigation,
  vaults,
  vaultsLoading,
}: {
  navigation: Nav;
  vaults: VaultListItem[];
  vaultsLoading: boolean;
}) {
  const preview = vaults.slice(0, 3);

  const activityQuery = useQuery({
    queryKey: ['transactions', 'home-preview', 'vault'],
    queryFn: () => fetchUnifiedTransactions({ limit: 3, scope: 'vault' }),
  });

  return (
    <>
      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Your vaults</Text>
        {vaults.length > 0 && (
          <TouchableOpacity onPress={() => navigation.navigate('VaultList')}>
            <Text style={styles.seeAll}>See all</Text>
          </TouchableOpacity>
        )}
      </View>
      {vaultsLoading ? (
        <ActivityIndicator style={styles.inlineSpinner} />
      ) : preview.length > 0 ? (
        preview.map(vault => (
          <VaultCard
            key={vault.id}
            vault={vault}
            onPress={() => navigation.navigate('VaultDetail', { vaultId: vault.id })}
          />
        ))
      ) : (
        <Text style={styles.emptySectionText}>No vaults yet.</Text>
      )}

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Vault activity</Text>
        <TouchableOpacity
          onPress={() => navigation.navigate('TransactionHistory', { scope: 'vault' })}
        >
          <Text style={styles.seeAll}>See all</Text>
        </TouchableOpacity>
      </View>
      <ActivityPreview items={activityQuery.data?.transactions} isLoading={activityQuery.isLoading} />
    </>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Wallet state body
// ─────────────────────────────────────────────────────────────────────────────

function WalletStateBody({
  navigation,
  susuGroups,
  susuLoading,
  walletLoading,
}: {
  navigation: Nav;
  susuGroups: import('../types/susu').SusuGroupListResponse[];
  susuLoading: boolean;
  walletLoading: boolean;
}) {
  const preview = susuGroups.slice(0, 3);

  const activityQuery = useQuery({
    queryKey: ['transactions', 'home-preview', 'wallet'],
    queryFn: () => fetchUnifiedTransactions({ limit: 3, scope: 'wallet' }),
  });

  return (
    <>
      {!walletLoading && (
        <IdlePayoutNudge onPress={() => navigation.navigate('AccountPicker', { mode: 'DEPOSIT' })} />
      )}

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Your susus</Text>
        {susuGroups.length > 0 && (
          <TouchableOpacity onPress={() => navigation.navigate('SusuList')}>
            <Text style={styles.seeAll}>See all</Text>
          </TouchableOpacity>
        )}
      </View>
      {susuLoading ? (
        <ActivityIndicator style={styles.inlineSpinner} />
      ) : preview.length > 0 ? (
        preview.map(group => (
          <SusuCard
            key={group.group_id}
            group={group}
            onPress={() => navigation.navigate('SusuDetail', { groupId: group.group_id })}
          />
        ))
      ) : (
        <Text style={styles.emptySectionText}>No susu groups yet.</Text>
      )}

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Wallet activity</Text>
        <TouchableOpacity
          onPress={() => navigation.navigate('TransactionHistory', { scope: 'wallet' })}
        >
          <Text style={styles.seeAll}>See all</Text>
        </TouchableOpacity>
      </View>
      <ActivityPreview items={activityQuery.data?.transactions} isLoading={activityQuery.isLoading} />
    </>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Idle susu-payout nudge
// ─────────────────────────────────────────────────────────────────────────────

/**
 * "A susu payout landed and hasn't been moved" heuristic: the most recent
 * wallet-scoped SUSU_DISBURSEMENT within the last 14 days, with no
 * VAULT_DEPOSIT since it landed. No new schema — derived entirely from
 * existing transaction history. Revisit if a more precise signal (e.g. an
 * explicit "unacknowledged payout" flag) is ever needed.
 */
function useIdlePayoutNudge() {
  return useQuery({
    queryKey: ['transactions', 'idle-payout-check'],
    queryFn: async () => {
      const [walletPage, vaultPage] = await Promise.all([
        fetchUnifiedTransactions({ scope: 'wallet', transactionType: 'SUSU_DISBURSEMENT', limit: 1 }),
        fetchUnifiedTransactions({ scope: 'vault', transactionType: 'DEPOSIT', limit: 1 }),
      ]);

      const lastPayout = walletPage.transactions[0];
      if (!lastPayout) return null;

      const payoutAgeMs = Date.now() - new Date(lastPayout.createdAt).getTime();
      const fourteenDaysMs = 14 * 24 * 60 * 60 * 1000;
      if (payoutAgeMs > fourteenDaysMs) return null;

      const lastVaultDeposit = vaultPage.transactions[0];
      const movedSincePayout =
        lastVaultDeposit && new Date(lastVaultDeposit.createdAt) > new Date(lastPayout.createdAt);
      if (movedSincePayout) return null;

      return lastPayout;
    },
  });
}

function IdlePayoutNudge({ onPress }: { onPress: () => void }) {
  const { data: payout } = useIdlePayoutNudge();

  if (!payout) return null;

  return (
    <TouchableOpacity
      style={styles.nudgeCard}
      onPress={onPress}
      activeOpacity={0.85}
      testID="idle-payout-nudge"
      accessibilityRole="button"
      accessibilityLabel={`GHS ${payout.amountCedis} is sitting in your wallet. Move it into a vault to keep it safe.`}
    >
      <Text style={styles.nudgeText}>
        <Text style={styles.nudgeAmount}>GHS {payout.amountCedis} is sitting in your wallet</Text> ·{' '}
        {payout.accountName} · move it into a vault to keep it safe.
      </Text>
    </TouchableOpacity>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Activity preview (shared by Savings/Wallet states)
// ─────────────────────────────────────────────────────────────────────────────

function ActivityPreview({
  items,
  isLoading,
}: {
  items: UnifiedTransactionItem[] | undefined;
  isLoading: boolean;
}) {
  if (isLoading) {
    return <ActivityIndicator style={styles.inlineSpinner} />;
  }
  if (!items || items.length === 0) {
    return <Text style={styles.emptySectionText}>No recent activity.</Text>;
  }
  return (
    <View style={styles.activityCard}>
      {items.map((item, index) => (
        <View
          key={item.transactionReference}
          style={[styles.activityRow, index === items.length - 1 && styles.activityRowLast]}
        >
          <View style={styles.activityInfo}>
            <Text style={styles.activityTitle} numberOfLines={1}>
              {item.accountName}
            </Text>
            <Text style={styles.activitySub}>{item.transactionType}</Text>
          </View>
          <Text style={[styles.activityAmount, item.direction === 'IN' && styles.activityAmountIn]}>
            {item.direction === 'IN' ? '+' : '-'}GHS {item.amountCedis}
          </Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: '#F9FAFB',
  },
  scroll: {
    flex: 1,
  },
  content: {
    paddingHorizontal: 16,
    paddingTop: Platform.OS === 'android' ? 16 : 12,
    paddingBottom: 100,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 20,
  },
  greeting: {
    fontSize: 22,
    fontWeight: '700',
    color: '#111827',
  },
  subhead: {
    fontSize: 14,
    color: '#6B7280',
    marginTop: 2,
  },
  headerButtons: {
    flexDirection: 'row',
    gap: 10,
  },
  bellButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: '#E5E7EB',
  },
  bellIcon: {
    fontSize: 18,
  },
  bellBadge: {
    position: 'absolute',
    top: -2,
    right: -2,
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: '#EF4444',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 3,
    borderWidth: 2,
    borderColor: '#F9FAFB',
  },
  bellBadgeText: {
    fontSize: 10,
    fontWeight: '700',
    color: '#FFFFFF',
  },

  segmentRow: {
    flexDirection: 'row',
    backgroundColor: '#F3F4F6',
    borderRadius: 12,
    padding: 4,
    marginBottom: 16,
  },
  segment: {
    flex: 1,
    paddingVertical: 9,
    borderRadius: 9,
    alignItems: 'center',
  },
  segmentActive: {
    backgroundColor: '#FFFFFF',
    shadowColor: '#000',
    shadowOpacity: 0.08,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 1 },
    elevation: 1,
  },
  segmentText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#6B7280',
  },
  segmentTextActive: {
    color: '#111827',
  },

  heroCard: {
    backgroundColor: '#1A1A1A',
    borderRadius: 18,
    padding: 24,
    marginBottom: 24,
  },
  heroLabel: {
    fontSize: 13,
    color: 'rgba(255,255,255,0.6)',
    fontWeight: '500',
    letterSpacing: 0.4,
    textTransform: 'uppercase',
    marginBottom: 8,
  },
  heroBalanceRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
  },
  heroCurrency: {
    fontSize: 18,
    color: 'rgba(255,255,255,0.8)',
    fontWeight: '500',
  },
  heroBalance: {
    fontSize: 36,
    fontWeight: '800',
    color: '#FFFFFF',
  },
  heroBalanceUnavailable: {
    fontSize: 28,
    fontWeight: '700',
    color: 'rgba(255,255,255,0.35)',
  },
  heroSub: {
    fontSize: 12,
    color: 'rgba(255,255,255,0.5)',
    marginTop: 8,
  },

  actionRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 28,
  },
  actionTile: {
    flex: 1,
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#E5E7EB',
  },
  actionIcon: {
    fontSize: 20,
    marginBottom: 6,
    color: '#1A1A1A',
    fontWeight: '700',
  },
  actionLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#111827',
  },

  summaryRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 28,
  },
  summaryTile: {
    flex: 1,
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#E5E7EB',
  },
  summaryValue: {
    fontSize: 22,
    fontWeight: '800',
    color: '#111827',
  },
  summaryLabel: {
    fontSize: 12,
    color: '#6B7280',
    marginTop: 2,
  },

  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 14,
    marginTop: 4,
  },
  sectionTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: '#111827',
  },
  seeAll: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1A1A1A',
  },
  emptySectionText: {
    fontSize: 13,
    color: '#9CA3AF',
    marginBottom: 20,
  },
  inlineSpinner: {
    marginBottom: 20,
  },
  negateInset: {
    marginHorizontal: -16,
    marginBottom: 8,
  },

  nudgeCard: {
    backgroundColor: '#ECFDF5',
    borderRadius: 12,
    padding: 14,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#A7F3D0',
  },
  nudgeText: {
    fontSize: 13,
    color: '#065F46',
    lineHeight: 19,
  },
  nudgeAmount: {
    fontWeight: '700',
  },

  activityCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#E5E7EB',
    overflow: 'hidden',
  },
  activityRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
  },
  activityRowLast: { borderBottomWidth: 0 },
  activityInfo: { flex: 1, marginRight: 8 },
  activityTitle: { fontSize: 14, fontWeight: '600', color: '#111827' },
  activitySub: { fontSize: 11, color: '#9CA3AF', marginTop: 2 },
  activityAmount: { fontSize: 14, fontWeight: '700', color: '#DC2626' },
  activityAmountIn: { color: '#059669' },
});
