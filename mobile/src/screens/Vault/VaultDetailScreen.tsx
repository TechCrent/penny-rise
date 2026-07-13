import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  RefreshControl,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQueryClient } from '@tanstack/react-query';
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { useVaultDetail } from '../../api/hooks/useVaultDetail';
import { useStatement, type StatementEntry } from '../../api/hooks/useStatement';
import type { VaultListItem } from '../../api/hooks/useVaults';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { VaultActionBar } from './components/VaultActionBar';
import { TransactionReceiptModal } from './components/TransactionReceiptModal';
import { PressableScale } from '../../components/ui';
import { colors, radii, spacing, typography } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'VaultDetail'>;
type RouteProps = RouteProp<RootStackParamList, 'VaultDetail'>;

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-GH', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

function formatTimestamp(iso: string): string {
  return new Date(iso).toLocaleString('en-GH', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

interface UnlockInfo {
  label: string;
  progress: number | null;
}

function buildUnlockLabel(vault: VaultListItem): UnlockInfo | null {
  if (vault.vault_type !== 'LOCKED') return null;

  const dateMet = vault.unlock_at ? new Date(vault.unlock_at) <= new Date() : null;
  const progressPercent =
    vault.unlock_amount && vault.balance_pesewas !== null
      ? Math.min((vault.balance_pesewas / vault.unlock_amount) * 100, 100)
      : null;

  if (vault.unlock_at && vault.unlock_amount) {
    const dateStr = new Date(vault.unlock_at).toLocaleDateString('en-GH', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
    const amtGhs = (vault.unlock_amount / 100).toLocaleString('en-GH', {
      minimumFractionDigits: 2,
    });
    const logic = vault.unlock_condition_logic === 'OR' ? 'or' : 'and';
    return { label: `Unlocks on ${dateStr} ${logic} at GHS ${amtGhs}`, progress: progressPercent };
  }
  if (vault.unlock_at) {
    const dateStr = new Date(vault.unlock_at).toLocaleDateString('en-GH', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
    const daysLeft = Math.max(
      0,
      Math.ceil((new Date(vault.unlock_at).getTime() - Date.now()) / (1000 * 60 * 60 * 24)),
    );
    return {
      label: dateMet
        ? `Unlock date reached (${dateStr})`
        : `Unlocks on ${dateStr} · ${daysLeft} day${daysLeft !== 1 ? 's' : ''} left`,
      progress: null,
    };
  }
  if (vault.unlock_amount) {
    const amtGhs = (vault.unlock_amount / 100).toLocaleString('en-GH', {
      minimumFractionDigits: 2,
    });
    return { label: `Goal: GHS ${amtGhs}`, progress: progressPercent };
  }
  return null;
}

// ─── Statement row ────────────────────────────────────────────────────────────

function StatementRow({ entry, onPress }: { entry: StatementEntry; onPress: () => void }) {
  const isCredit = entry.direction === 'CREDIT';
  return (
    <PressableScale
      style={txStyles.row}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${isCredit ? 'CREDIT' : 'DEBIT'} GHS ${entry.amount_cedis}, ${entry.transaction_type}`}
    >
      <View style={[txStyles.dirDot, isCredit ? txStyles.dotCredit : txStyles.dotDebit]} />
      <View style={txStyles.rowBody}>
        <Text style={txStyles.txType} numberOfLines={1}>
          {entry.transaction_type.replace(/_/g, ' ')}
        </Text>
        {entry.narrative ? (
          <Text style={txStyles.narrative} numberOfLines={1}>
            {entry.narrative}
          </Text>
        ) : null}
        <Text style={txStyles.timestamp}>{formatTimestamp(entry.created_at)}</Text>
      </View>
      <Text style={[txStyles.amount, isCredit ? txStyles.amountCredit : txStyles.amountDebit]}>
        {isCredit ? '+' : '−'} {entry.amount_cedis}
      </Text>
    </PressableScale>
  );
}

// ─── MetaRow ──────────────────────────────────────────────────────────────────

function MetaRow({ label, value, last }: { label: string; value: string; last?: boolean }) {
  return (
    <View style={[metaStyles.row, !last && metaStyles.border]}>
      <Text style={metaStyles.label}>{label}</Text>
      <Text style={metaStyles.value}>{value}</Text>
    </View>
  );
}

// ─── VaultDetailScreen ────────────────────────────────────────────────────────

export default function VaultDetailScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<RouteProps>();
  const queryClient = useQueryClient();

  const { vaultId, successMessage } = route.params;

  const [selectedRef, setSelectedRef] = useState<string | null>(null);
  const [showSuccess, setShowSuccess] = useState(!!successMessage);

  const {
    data: vault,
    isLoading: vaultLoading,
    isError: vaultErrored,
    error: vaultError,
    refetch: refetchVault,
    isFetching: vaultFetching,
  } = useVaultDetail(vaultId);

  const {
    data: statementData,
    isLoading: statementLoading,
    isFetchingNextPage,
    hasNextPage,
    fetchNextPage,
    refetch: refetchStatement,
  } = useStatement(vaultId, !!vaultId);

  const allEntries: StatementEntry[] = (statementData?.pages ?? []).flatMap(p => p.entries);

  const onRefresh = useCallback(async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['vault', vaultId] }),
      queryClient.invalidateQueries({ queryKey: ['statement', vaultId] }),
      refetchVault(),
      refetchStatement(),
    ]);
  }, [queryClient, vaultId, refetchVault, refetchStatement]);

  if (vaultLoading) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.loadingCenter}>
          <ActivityIndicator size="large" color={colors.gold.base} />
        </View>
      </SafeAreaView>
    );
  }

  if (vaultErrored || !vault) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.loadingCenter}>
          <Text style={styles.errorText}>
            {vaultError instanceof Error ? vaultError.message : 'Could not load this vault.'}
          </Text>
          <PressableScale style={styles.retryBtn} onPress={() => refetchVault()}>
            <Text style={styles.retryText}>Retry</Text>
          </PressableScale>
        </View>
      </SafeAreaView>
    );
  }

  const unlockInfo = buildUnlockLabel(vault);
  const isLocked = vault.vault_type === 'LOCKED';
  const isEarlyExit = vault.status === 'EARLY_EXIT_PENDING';

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          style={styles.backBtn}
          accessibilityLabel="Go back"
        >
          <Text style={styles.backIcon}>←</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle} numberOfLines={1}>
          {vault.name}
        </Text>
        <View style={styles.backBtn} />
      </View>

      <FlatList
        data={allEntries}
        keyExtractor={item => item.entry_id}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={vaultFetching && !vaultLoading}
            onRefresh={onRefresh}
            tintColor={colors.gold.base}
            colors={[colors.gold.base]}
          />
        }
        ListHeaderComponent={
          <View>
            {showSuccess && successMessage && (
              <TouchableOpacity
                style={styles.successBanner}
                onPress={() => setShowSuccess(false)}
                activeOpacity={0.9}
              >
                <Ionicons name="checkmark-circle" size={15} color={colors.status.successText} />
                <Text style={styles.successText}>{successMessage}</Text>
              </TouchableOpacity>
            )}

            <LinearGradient
              colors={[colors.heroFrom, colors.heroTo]}
              start={{ x: 0, y: 0 }}
              end={{ x: 1, y: 1 }}
              style={styles.heroCard}
            >
              <View style={styles.heroTop}>
                <Text style={styles.heroLabel}>Current balance</Text>
                <View
                  style={[styles.typeBadge, isLocked ? styles.badgeLocked : styles.badgeStandard]}
                >
                  <Text style={styles.badgeText}>{isLocked ? 'LOCKED' : 'STANDARD'}</Text>
                </View>
              </View>

              {vault.balance_pesewas === null ? (
                <Text style={styles.heroBalanceUnavailable}>—</Text>
              ) : (
                <View style={styles.heroBalanceRow}>
                  <Text style={styles.heroCurrency}>GHS </Text>
                  <Text style={styles.heroBalance}>{vault.balance_cedis}</Text>
                </View>
              )}

              {isLocked && unlockInfo && (
                <View style={styles.unlockSection}>
                  <Text style={styles.unlockLabel}>{unlockInfo.label}</Text>
                  {unlockInfo.progress !== null && (
                    <View style={styles.progressTrack}>
                      {/* eslint-disable-next-line react-native/no-inline-styles */}
                      <View style={[styles.progressFill, { width: `${unlockInfo.progress}%` }]} />
                    </View>
                  )}
                  {unlockInfo.progress !== null && (
                    <Text style={styles.progressPct}>
                      {unlockInfo.progress.toFixed(0)}% of goal
                      {unlockInfo.progress >= 100 ? ' — Goal reached! 🎉' : ''}
                    </Text>
                  )}
                </View>
              )}
            </LinearGradient>

            <View style={styles.metaCard}>
              <MetaRow label="Vault type" value={isLocked ? 'Locked vault' : 'Standard vault'} />
              <MetaRow
                label="Status"
                value={isEarlyExit ? 'Early exit pending' : vault.status.toLowerCase()}
              />
              <MetaRow label="Created" value={formatDate(vault.created_at)} last />
            </View>

            <Text style={styles.sectionTitle}>Transaction history</Text>
          </View>
        }
        renderItem={({ item }) => (
          <StatementRow entry={item} onPress={() => setSelectedRef(item.transaction_reference)} />
        )}
        ListEmptyComponent={
          statementLoading ? (
            <View style={styles.statementLoading}>
              <ActivityIndicator size="small" color={colors.gold.base} />
            </View>
          ) : (
            <View style={styles.emptyStatement}>
              <View style={styles.emptyIconWrap}>
                <Ionicons name="receipt-outline" size={22} color={colors.neutral[400]} />
              </View>
              <Text style={styles.emptyText}>No transactions yet</Text>
              <Text style={styles.emptySubtext}>
                Your transaction history will appear here after your first deposit.
              </Text>
            </View>
          )
        }
        onEndReached={() => {
          if (hasNextPage && !isFetchingNextPage) fetchNextPage();
        }}
        onEndReachedThreshold={0.4}
        ListFooterComponent={
          isFetchingNextPage ? (
            <View style={styles.loadMore}>
              <ActivityIndicator size="small" color={colors.gold.base} />
            </View>
          ) : null
        }
        contentContainerStyle={styles.listContent}
      />

      <VaultActionBar
        vault={vault}
        onDeposit={() => navigation.navigate('Deposit', { vaultId })}
        onWithdraw={() => navigation.navigate('Withdraw', { vaultId })}
        onEarlyExit={() => navigation.navigate('EarlyExit', { vaultId })}
        onCancelEarlyExit={() => navigation.navigate('CancelEarlyExit', { vaultId })}
      />

      <TransactionReceiptModal reference={selectedRef} onClose={() => setSelectedRef(null)} />
    </SafeAreaView>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  loadingCenter: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing['3xl'] },
  errorText: { color: colors.status.error, fontSize: 14, textAlign: 'center', marginBottom: spacing.md },
  retryBtn: {
    backgroundColor: colors.gold.base,
    paddingHorizontal: spacing.xl,
    paddingVertical: spacing.md,
    borderRadius: radii.sm,
  },
  retryText: { color: colors.neutral[900], fontSize: 14, fontWeight: '700' },
  listContent: { paddingBottom: spacing.xl },

  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    backgroundColor: colors.background,
  },
  backBtn: { width: 40, height: 40, justifyContent: 'center' },
  backIcon: { fontSize: 22, color: colors.textPrimary },
  headerTitle: { ...typography.h3, color: colors.textPrimary, flex: 1, textAlign: 'center' },

  successBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    backgroundColor: colors.status.successBg,
    borderRadius: radii.sm,
    margin: spacing.lg,
    padding: spacing.md,
    justifyContent: 'center',
  },
  successText: { fontSize: 13, fontWeight: '600', color: colors.status.successText },

  heroCard: {
    margin: spacing.lg,
    borderRadius: radii['2xl'],
    padding: spacing.xl,
  },
  heroTop: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.sm,
  },
  heroLabel: {
    fontSize: 12,
    color: colors.textOnDarkMuted,
    fontWeight: '500',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  typeBadge: { borderRadius: 6, paddingHorizontal: spacing.sm, paddingVertical: 3 },
  badgeLocked: { backgroundColor: 'rgba(255,255,255,0.15)' },
  badgeStandard: { backgroundColor: 'rgba(245,183,0,0.25)' },
  badgeText: { fontSize: 10, fontWeight: '700', color: colors.textOnDark, letterSpacing: 0.5 },
  heroBalanceRow: { flexDirection: 'row', alignItems: 'baseline' },
  heroCurrency: { fontSize: 16, color: colors.textOnDarkMuted, fontWeight: '500' },
  heroBalance: { fontSize: 40, fontWeight: '800', color: colors.textOnDark, letterSpacing: -1.5 },
  heroBalanceUnavailable: { fontSize: 32, fontWeight: '700', color: colors.textOnDarkFaint },

  unlockSection: {
    marginTop: spacing.lg,
    borderTopWidth: 1,
    borderTopColor: 'rgba(255,255,255,0.1)',
    paddingTop: spacing.md,
  },
  unlockLabel: { fontSize: 12, color: colors.textOnDarkMuted, marginBottom: spacing.sm },
  progressTrack: {
    height: 4,
    backgroundColor: 'rgba(255,255,255,0.15)',
    borderRadius: 2,
    overflow: 'hidden',
  },
  progressFill: { height: '100%', backgroundColor: colors.gold.base, borderRadius: 2 },
  progressPct: { fontSize: 11, color: colors.textOnDarkFaint, marginTop: spacing.xs, textAlign: 'right' },

  metaCard: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    marginHorizontal: spacing.lg,
    marginBottom: spacing.xl,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: colors.textSecondary,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginHorizontal: spacing.lg,
    marginBottom: spacing.sm,
  },

  statementLoading: { paddingVertical: spacing['2xl'], alignItems: 'center' },
  loadMore: { paddingVertical: spacing.lg, alignItems: 'center' },
  emptyStatement: { alignItems: 'center', paddingVertical: spacing['4xl'], paddingHorizontal: spacing['3xl'] },
  emptyIconWrap: {
    width: 44,
    height: 44,
    borderRadius: radii.pill,
    backgroundColor: colors.neutral[100],
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.md,
  },
  emptyText: { fontSize: 16, fontWeight: '700', color: colors.textPrimary, marginBottom: spacing.xs },
  emptySubtext: { fontSize: 13, color: colors.textSecondary, textAlign: 'center', lineHeight: 19 },
});

const metaStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
  },
  border: { borderBottomWidth: 1, borderBottomColor: colors.neutral[100] },
  label: {
    fontSize: 12,
    color: colors.textSecondary,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  value: { fontSize: 13, fontWeight: '600', color: colors.textPrimary },
});

const txStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingHorizontal: spacing.lg,
    paddingVertical: 13,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.neutral[100],
  },
  dirDot: { width: 8, height: 8, borderRadius: 4, flexShrink: 0 },
  dotCredit: { backgroundColor: colors.status.success },
  dotDebit: { backgroundColor: colors.status.error },
  rowBody: { flex: 1 },
  txType: { fontSize: 13, fontWeight: '600', color: colors.textPrimary, textTransform: 'capitalize' },
  narrative: { fontSize: 12, color: colors.textSecondary, marginTop: 2 },
  timestamp: { fontSize: 11, color: colors.textTertiary, marginTop: 3 },
  amount: { fontSize: 14, fontWeight: '700' },
  amountCredit: { color: colors.status.successText },
  amountDebit: { color: colors.status.error },
});
