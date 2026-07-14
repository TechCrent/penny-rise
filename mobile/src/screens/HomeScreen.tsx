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
import { LinearGradient } from 'expo-linear-gradient';
import Animated from 'react-native-reanimated';
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
import {
  AnimatedNumber,
  EmptyState,
  Icon,
  PressableScale,
  ProgressRing,
  fadeInUp,
} from '../components/ui';
import type { IconName } from '../components/ui';
import { colors, radii, shadows, spacing, typography } from '../theme';
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
  const {
    balance: walletBalance,
    loading: walletLoading,
    fetch: fetchWalletBalance,
  } = useWalletBalance();
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
  const grandTotalPesewas = vaultTotalPesewas !== null ? vaultTotalPesewas + walletPesewas : null;

  const activeChallenges = (challenges ?? []).filter(c => sectionFor(c) === 'ACTIVE');

  if (vaultsLoading) {
    return (
      <SafeAreaView style={styles.safe}>
        <HomeSkeleton />
      </SafeAreaView>
    );
  }

  const heroValue =
    homeState === 'TOTAL'
      ? grandTotalPesewas
      : homeState === 'SAVINGS'
        ? vaultTotalPesewas
        : walletPesewas;
  const heroLabel =
    homeState === 'TOTAL'
      ? 'Total balance'
      : homeState === 'SAVINGS'
        ? 'Savings total'
        : 'Wallet balance';

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={isFetching && !vaultsLoading}
            onRefresh={onRefresh}
            tintColor={colors.gold.base}
            colors={[colors.gold.base]}
          />
        }
      >
        <View style={styles.header}>
          <View style={styles.headerLeft}>
            <PressableScale
              style={styles.avatar}
              onPress={() => navigation.navigate('Profile')}
              accessibilityRole="button"
              accessibilityLabel="Open profile"
            >
              <Icon name="person" size={18} color={colors.gold.text} />
            </PressableScale>
            <View>
              <Text style={styles.greeting}>{greeting()}</Text>
              <Text style={styles.subhead}>Here&apos;s how your money is doing</Text>
            </View>
          </View>
          <View style={styles.headerButtons}>
            <PressableScale
              style={styles.bellButton}
              onPress={() => navigation.navigate('Notifications')}
              accessibilityRole="button"
              accessibilityLabel="Notifications"
            >
              <Icon name="notifications-outline" size={19} color={colors.textPrimary} />
              {unreadNotificationsCount > 0 && (
                <View style={styles.bellBadge}>
                  <Text style={styles.bellBadgeText}>
                    {unreadNotificationsCount > 9 ? '9+' : unreadNotificationsCount}
                  </Text>
                </View>
              )}
            </PressableScale>
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

        <LinearGradient
          colors={[colors.heroFrom, colors.heroTo]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={styles.heroCard}
        >
          <View style={styles.heroTopRow}>
            <Text style={styles.heroLabel}>{heroLabel}</Text>
            <View style={styles.heroIconBadge}>
              <Icon name="sparkles" size={13} color={colors.gold.base} />
            </View>
          </View>
          {heroValue === null ? (
            <Text style={styles.heroBalanceUnavailable}>
              {vaultsError ? 'Could not load balances' : '—'}
            </Text>
          ) : (
            <View style={styles.heroBalanceRow}>
              <Text style={styles.heroCurrency}>GHS </Text>
              <AnimatedNumber
                value={heroValue}
                formatter={formatCedis}
                style={styles.heroBalance}
              />
            </View>
          )}
          {homeState === 'TOTAL' && (
            <Text style={styles.heroSub}>
              Across {vaultsData?.total_count ?? 0} vault
              {(vaultsData?.total_count ?? 0) !== 1 ? 's' : ''} + wallet
            </Text>
          )}
        </LinearGradient>

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

const QUICK_ACTIONS: {
  key: string;
  label: string;
  icon: IconName;
  emphasis?: boolean;
}[] = [
  { key: 'deposit', label: 'Deposit', icon: 'arrow-down', emphasis: true },
  { key: 'send', label: 'Send', icon: 'arrow-forward' },
  { key: 'withdraw', label: 'Withdraw', icon: 'arrow-up' },
];

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
  const onAction = (key: string) => {
    if (key === 'deposit') {
      hasVaults
        ? navigation.navigate('AccountPicker', { mode: 'DEPOSIT' })
        : navigation.navigate('CreateVault');
    } else if (key === 'send') {
      navigation.navigate('RecipientPicker');
    } else {
      hasVaults
        ? navigation.navigate('AccountPicker', { mode: 'WITHDRAW' })
        : navigation.navigate('CreateVault');
    }
  };

  return (
    <>
      <Animated.View entering={fadeInUp(60)} style={styles.actionRow}>
        {QUICK_ACTIONS.map(action => (
          <PressableScale
            key={action.key}
            style={styles.actionTile}
            onPress={() => onAction(action.key)}
            accessibilityRole="button"
            accessibilityLabel={action.label}
          >
            <View style={[styles.actionIconWrap, action.emphasis && styles.actionIconWrapGold]}>
              <Icon
                name={action.icon}
                size={18}
                color={action.emphasis ? colors.neutral[900] : colors.textPrimary}
              />
            </View>
            <Text style={styles.actionLabel}>{action.label}</Text>
          </PressableScale>
        ))}
      </Animated.View>

      <Animated.View entering={fadeInUp(120)} style={styles.summaryRow} testID="portfolio-summary">
        <View style={styles.summaryTile}>
          <Text style={styles.summaryValue}>{vaultCount}</Text>
          <Text style={styles.summaryLabel}>vault{vaultCount !== 1 ? 's' : ''}</Text>
        </View>
        <View style={styles.summaryTile}>
          <Text style={styles.summaryValue}>{susuCount}</Text>
          <Text style={styles.summaryLabel}>susu group{susuCount !== 1 ? 's' : ''}</Text>
        </View>
      </Animated.View>

      <Animated.View entering={fadeInUp(160)}>
        <PortfolioInsight vaultCount={vaultCount} susuCount={susuCount} />
      </Animated.View>

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
        <EmptyState
          icon="trophy-outline"
          title="No active challenges yet"
          message="Join one from the Challenges tab to build a savings habit."
        />
      )}
    </>
  );
}

/**
 * A short, data-derived line — not a fabricated "insight." Only reflects
 * real portfolio shape (vault/susu counts) already fetched for this screen.
 */
function PortfolioInsight({ vaultCount, susuCount }: { vaultCount: number; susuCount: number }) {
  if (vaultCount === 0 && susuCount === 0) {
    return (
      <View style={styles.insightCard}>
        <View style={styles.insightIconWrap}>
          <Icon name="flash-outline" size={16} color={colors.gold.text} />
        </View>
        <Text style={styles.insightText}>
          Start your first vault to begin building your savings.
        </Text>
      </View>
    );
  }
  return (
    <View style={styles.insightCard}>
      <View style={styles.insightIconWrap}>
        <Icon name="flash-outline" size={16} color={colors.gold.text} />
      </View>
      <Text style={styles.insightText}>
        You&apos;re actively saving across {vaultCount} vault{vaultCount !== 1 ? 's' : ''}
        {susuCount > 0 ? ` and ${susuCount} susu group${susuCount !== 1 ? 's' : ''}` : ''}. Keep it
        up.
      </Text>
    </View>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Savings state body
// ─────────────────────────────────────────────────────────────────────────────

function goalProgress(vault: VaultListItem): number | null {
  if (!vault.unlock_amount || vault.balance_pesewas === null) return null;
  return vault.balance_pesewas / vault.unlock_amount;
}

function GoalRings({ vaults }: { vaults: VaultListItem[] }) {
  const withGoals = vaults.filter(v => v.unlock_amount !== null && v.balance_pesewas !== null);
  if (withGoals.length === 0) return null;

  return (
    <Animated.View entering={fadeInUp(40)}>
      <Text style={styles.goalRingCaption}>Goal progress</Text>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.goalRingScroll}
        contentContainerStyle={styles.goalRingContent}
      >
        {withGoals.map(vault => {
          const progress = goalProgress(vault) ?? 0;
          const percent = Math.round(Math.min(1, progress) * 100);
          return (
            <View
              key={vault.id}
              style={styles.goalRingItem}
              accessible
              accessibilityLabel={`${vault.name}, ${percent}% of goal`}
            >
              <ProgressRing progress={progress} size={56} strokeWidth={5}>
                <Text style={styles.goalRingPercent}>{percent}%</Text>
              </ProgressRing>
            </View>
          );
        })}
      </ScrollView>
    </Animated.View>
  );
}

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
      <GoalRings vaults={vaults} />

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
        preview.map((vault, i) => (
          <Animated.View key={vault.id} entering={fadeInUp(60 * i)}>
            <VaultCard
              vault={vault}
              onPress={() => navigation.navigate('VaultDetail', { vaultId: vault.id })}
            />
          </Animated.View>
        ))
      ) : (
        <EmptyState
          icon="lock-closed-outline"
          title="No vaults yet"
          message="Create a vault to start setting money aside."
        />
      )}

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Vault activity</Text>
        <TouchableOpacity
          onPress={() => navigation.navigate('TransactionHistory', { scope: 'vault' })}
        >
          <Text style={styles.seeAll}>See all</Text>
        </TouchableOpacity>
      </View>
      <ActivityPreview
        items={activityQuery.data?.transactions}
        isLoading={activityQuery.isLoading}
      />
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
        <IdlePayoutNudge
          onPress={() => navigation.navigate('AccountPicker', { mode: 'DEPOSIT' })}
        />
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
        preview.map((group, i) => (
          <Animated.View key={group.group_id} entering={fadeInUp(60 * i)}>
            <SusuCard
              group={group}
              onPress={() => navigation.navigate('SusuDetail', { groupId: group.group_id })}
            />
          </Animated.View>
        ))
      ) : (
        <EmptyState
          icon="people-outline"
          title="No susu groups yet"
          message="Join or create a susu group to save with others."
        />
      )}

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Wallet activity</Text>
        <TouchableOpacity
          onPress={() => navigation.navigate('TransactionHistory', { scope: 'wallet' })}
        >
          <Text style={styles.seeAll}>See all</Text>
        </TouchableOpacity>
      </View>
      <ActivityPreview
        items={activityQuery.data?.transactions}
        isLoading={activityQuery.isLoading}
      />
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
        fetchUnifiedTransactions({
          scope: 'wallet',
          transactionType: 'SUSU_DISBURSEMENT',
          limit: 1,
        }),
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
    <Animated.View entering={fadeInUp(0)}>
      <PressableScale
        style={styles.nudgeCard}
        onPress={onPress}
        testID="idle-payout-nudge"
        accessibilityRole="button"
        accessibilityLabel={`GHS ${payout.amountCedis} is sitting in your wallet. Move it into a vault to keep it safe.`}
      >
        <View style={styles.nudgeIconWrap}>
          <Icon name="sparkles" size={15} color={colors.gold.text} />
        </View>
        <Text style={styles.nudgeText}>
          <Text style={styles.nudgeAmount}>GHS {payout.amountCedis} is sitting in your wallet</Text>{' '}
          · {payout.accountName} · move it into a vault to keep it safe.
        </Text>
      </PressableScale>
    </Animated.View>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Activity preview (shared by Savings/Wallet states)
// ─────────────────────────────────────────────────────────────────────────────

const TX_ICON: Record<string, IconName> = {
  DEPOSIT: 'arrow-down-circle-outline',
  WITHDRAWAL: 'arrow-up-circle-outline',
  TRANSFER: 'swap-horizontal-outline',
  SUSU_CONTRIBUTION: 'people-circle-outline',
  SUSU_DISBURSEMENT: 'gift-outline',
};

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
    return (
      <EmptyState icon="receipt-outline" title="No recent activity" testID="activity-empty-state" />
    );
  }
  return (
    <Animated.View entering={fadeInUp(0)} style={styles.activityCard}>
      {items.map((item, index) => (
        <View
          key={item.transactionReference}
          style={[styles.activityRow, index === items.length - 1 && styles.activityRowLast]}
        >
          <View style={styles.activityIconWrap}>
            <Icon
              name={TX_ICON[item.transactionType] ?? 'ellipse-outline'}
              size={18}
              color={colors.neutral[600]}
            />
          </View>
          <View style={styles.activityInfo}>
            <Text style={styles.activityTitle} numberOfLines={1}>
              {item.accountName}
            </Text>
            <Text style={styles.activitySub}>
              {item.transactionType}
              {item.status !== 'COMPLETED' ? ` · ${item.status.toLowerCase()}` : ''}
            </Text>
          </View>
          <Text style={[styles.activityAmount, item.direction === 'IN' && styles.activityAmountIn]}>
            {item.direction === 'IN' ? '+' : '-'}GHS {item.amountCedis}
          </Text>
        </View>
      ))}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: colors.background,
  },
  scroll: {
    flex: 1,
  },
  content: {
    paddingHorizontal: spacing.lg,
    paddingTop: Platform.OS === 'android' ? spacing.lg : spacing.md,
    paddingBottom: 100,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.xl,
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flexShrink: 1,
  },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: spacing.md,
  },
  greeting: {
    ...typography.h2,
    color: colors.textPrimary,
  },
  subhead: {
    ...typography.caption,
    color: colors.textSecondary,
    marginTop: 2,
  },
  headerButtons: {
    flexDirection: 'row',
    gap: spacing.sm,
  },
  bellButton: {
    width: 42,
    height: 42,
    borderRadius: radii.pill,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.border,
    ...shadows.sm,
  },
  bellBadge: {
    position: 'absolute',
    top: -2,
    right: -2,
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: colors.status.error,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 3,
    borderWidth: 2,
    borderColor: colors.background,
  },
  bellBadgeText: {
    fontSize: 10,
    fontWeight: '700',
    color: colors.neutral[0],
  },

  segmentRow: {
    flexDirection: 'row',
    backgroundColor: colors.neutral[100],
    borderRadius: radii.md,
    padding: 4,
    marginBottom: spacing.lg,
  },
  segment: {
    flex: 1,
    paddingVertical: 9,
    borderRadius: radii.sm,
    alignItems: 'center',
  },
  segmentActive: {
    backgroundColor: colors.surface,
    ...shadows.sm,
  },
  segmentText: {
    ...typography.caption,
    fontWeight: '600',
    color: colors.textSecondary,
  },
  segmentTextActive: {
    color: colors.textPrimary,
  },

  heroCard: {
    borderRadius: radii['2xl'],
    padding: spacing['2xl'],
    marginBottom: spacing['2xl'],
    ...shadows.lg,
  },
  heroTopRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.sm,
  },
  heroLabel: {
    ...typography.label,
    color: colors.textOnDarkMuted,
  },
  heroIconBadge: {
    width: 26,
    height: 26,
    borderRadius: radii.pill,
    backgroundColor: 'rgba(245,183,0,0.16)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroBalanceRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
  },
  heroCurrency: {
    fontSize: 18,
    color: colors.textOnDarkMuted,
    fontWeight: '500',
  },
  heroBalance: {
    ...typography.numericHero,
    color: colors.textOnDark,
  },
  heroBalanceUnavailable: {
    fontSize: 28,
    fontWeight: '700',
    color: colors.textOnDarkFaint,
  },
  heroSub: {
    ...typography.caption,
    color: colors.textOnDarkFaint,
    marginTop: spacing.sm,
  },

  actionRow: {
    flexDirection: 'row',
    gap: spacing.md,
    marginBottom: spacing['3xl'],
  },
  actionTile: {
    flex: 1,
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    paddingVertical: spacing.lg,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: colors.border,
  },
  actionIconWrap: {
    width: 34,
    height: 34,
    borderRadius: radii.pill,
    backgroundColor: colors.neutral[100],
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.sm,
  },
  actionIconWrapGold: {
    backgroundColor: colors.gold.base,
  },
  actionLabel: {
    ...typography.caption,
    fontWeight: '600',
    color: colors.textPrimary,
  },

  summaryRow: {
    flexDirection: 'row',
    gap: spacing.md,
    marginBottom: spacing.lg,
  },
  summaryTile: {
    flex: 1,
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    paddingVertical: spacing.lg,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: colors.border,
  },
  summaryValue: {
    ...typography.numericLarge,
    color: colors.textPrimary,
  },
  summaryLabel: {
    ...typography.caption,
    color: colors.textSecondary,
    marginTop: 2,
  },

  insightCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.gold.light,
    borderRadius: radii.lg,
    padding: spacing.md,
    marginBottom: spacing['3xl'],
  },
  insightIconWrap: {
    width: 30,
    height: 30,
    borderRadius: radii.pill,
    backgroundColor: colors.neutral[0],
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: spacing.md,
  },
  insightText: {
    ...typography.caption,
    color: colors.gold.text,
    flex: 1,
    lineHeight: 18,
  },

  goalRingCaption: {
    ...typography.label,
    color: colors.textTertiary,
    marginBottom: spacing.sm,
  },
  goalRingScroll: {
    marginBottom: spacing.lg,
    marginHorizontal: -spacing.lg,
  },
  goalRingContent: {
    paddingHorizontal: spacing.lg,
    gap: spacing.lg,
  },
  goalRingItem: {
    alignItems: 'center',
    width: 76,
  },
  goalRingPercent: {
    ...typography.caption,
    fontWeight: '700',
    color: colors.textPrimary,
  },

  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.md,
    marginTop: spacing.xxs,
  },
  sectionTitle: {
    ...typography.h3,
    color: colors.textPrimary,
  },
  seeAll: {
    ...typography.caption,
    fontWeight: '600',
    color: colors.textPrimary,
  },
  inlineSpinner: {
    marginBottom: spacing.xl,
  },
  negateInset: {
    marginHorizontal: -spacing.lg,
    marginBottom: spacing.sm,
  },

  nudgeCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.status.successBg,
    borderRadius: radii.lg,
    padding: spacing.md,
    marginBottom: spacing.xl,
  },
  nudgeIconWrap: {
    width: 28,
    height: 28,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: spacing.md,
  },
  nudgeText: {
    flex: 1,
    fontSize: 13,
    color: colors.status.successText,
    lineHeight: 19,
  },
  nudgeAmount: {
    fontWeight: '700',
  },

  activityCard: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    marginBottom: spacing.xl,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
  },
  activityRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.neutral[100],
  },
  activityRowLast: { borderBottomWidth: 0 },
  activityIconWrap: {
    width: 34,
    height: 34,
    borderRadius: radii.pill,
    backgroundColor: colors.neutral[100],
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: spacing.md,
  },
  activityInfo: { flex: 1, marginRight: spacing.sm },
  activityTitle: { ...typography.bodyMedium, color: colors.textPrimary },
  activitySub: { ...typography.caption, color: colors.textTertiary, marginTop: 2 },
  activityAmount: { ...typography.numericMedium, color: colors.status.error },
  activityAmountIn: { color: colors.status.success },
});
