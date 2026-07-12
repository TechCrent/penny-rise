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
import { useVaultDetail } from '../../api/hooks/useVaultDetail';
import { useStatement, type StatementEntry } from '../../api/hooks/useStatement';
import type { VaultListItem } from '../../api/hooks/useVaults';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { VaultActionBar } from './components/VaultActionBar';
import { TransactionReceiptModal } from './components/TransactionReceiptModal';

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
    <TouchableOpacity
      style={txStyles.row}
      onPress={onPress}
      activeOpacity={0.82}
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
    </TouchableOpacity>
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
          <ActivityIndicator size="large" color={INDIGO} />
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
          <TouchableOpacity style={styles.retryBtn} onPress={() => refetchVault()}>
            <Text style={styles.retryText}>Retry</Text>
          </TouchableOpacity>
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
            tintColor={INDIGO}
            colors={[INDIGO]}
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
                <Text style={styles.successText}>✓ {successMessage}</Text>
              </TouchableOpacity>
            )}

            <View style={styles.heroCard}>
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
            </View>

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
              <ActivityIndicator size="small" color={INDIGO} />
            </View>
          ) : (
            <View style={styles.emptyStatement}>
              <Text style={styles.emptyIcon}>📋</Text>
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
              <ActivityIndicator size="small" color={INDIGO} />
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

const INDIGO = '#4F46E5';
const DARK = '#1A1A2E';
const MUTED = '#6B7280';
const BACKGROUND = '#F8F9FF';

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: BACKGROUND },
  loadingCenter: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 32 },
  errorText: { color: '#EF4444', fontSize: 14, textAlign: 'center', marginBottom: 16 },
  retryBtn: {
    backgroundColor: INDIGO,
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 10,
  },
  retryText: { color: '#FFFFFF', fontSize: 14, fontWeight: '700' },
  listContent: { paddingBottom: 24 },

  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#EDEDF0',
    backgroundColor: BACKGROUND,
  },
  backBtn: { width: 40, height: 40, justifyContent: 'center' },
  backIcon: { fontSize: 22, color: DARK },
  headerTitle: { fontSize: 17, fontWeight: '700', color: DARK, flex: 1, textAlign: 'center' },

  successBanner: {
    backgroundColor: '#ECFDF5',
    borderRadius: 10,
    margin: 16,
    padding: 12,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#6EE7B7',
  },
  successText: { fontSize: 13, fontWeight: '600', color: '#065F46' },

  heroCard: {
    backgroundColor: DARK,
    margin: 16,
    borderRadius: 20,
    padding: 22,
    shadowColor: DARK,
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.16,
    shadowRadius: 14,
    elevation: 5,
  },
  heroTop: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  heroLabel: {
    fontSize: 12,
    color: 'rgba(255,255,255,0.6)',
    fontWeight: '500',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  typeBadge: { borderRadius: 6, paddingHorizontal: 8, paddingVertical: 3 },
  badgeLocked: { backgroundColor: 'rgba(255,255,255,0.15)' },
  badgeStandard: { backgroundColor: 'rgba(79,70,229,0.4)' },
  badgeText: { fontSize: 10, fontWeight: '700', color: '#FFFFFF', letterSpacing: 0.5 },
  heroBalanceRow: { flexDirection: 'row', alignItems: 'baseline' },
  heroCurrency: { fontSize: 16, color: 'rgba(255,255,255,0.7)', fontWeight: '500' },
  heroBalance: { fontSize: 40, fontWeight: '800', color: '#FFFFFF', letterSpacing: -1.5 },
  heroBalanceUnavailable: { fontSize: 32, fontWeight: '700', color: 'rgba(255,255,255,0.3)' },

  unlockSection: {
    marginTop: 16,
    borderTopWidth: 1,
    borderTopColor: 'rgba(255,255,255,0.1)',
    paddingTop: 14,
  },
  unlockLabel: { fontSize: 12, color: 'rgba(255,255,255,0.65)', marginBottom: 8 },
  progressTrack: {
    height: 4,
    backgroundColor: 'rgba(255,255,255,0.15)',
    borderRadius: 2,
    overflow: 'hidden',
  },
  progressFill: { height: '100%', backgroundColor: '#818CF8', borderRadius: 2 },
  progressPct: { fontSize: 11, color: 'rgba(255,255,255,0.5)', marginTop: 6, textAlign: 'right' },

  metaCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 14,
    marginHorizontal: 16,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#EDEDF0',
    overflow: 'hidden',
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: MUTED,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginHorizontal: 16,
    marginBottom: 8,
  },

  statementLoading: { paddingVertical: 32, alignItems: 'center' },
  loadMore: { paddingVertical: 16, alignItems: 'center' },
  emptyStatement: { alignItems: 'center', paddingVertical: 40, paddingHorizontal: 32 },
  emptyIcon: { fontSize: 40, marginBottom: 12 },
  emptyText: { fontSize: 16, fontWeight: '700', color: DARK, marginBottom: 6 },
  emptySubtext: { fontSize: 13, color: MUTED, textAlign: 'center', lineHeight: 19 },
});

const metaStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  border: { borderBottomWidth: 1, borderBottomColor: '#F3F4F6' },
  label: {
    fontSize: 12,
    color: MUTED,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  value: { fontSize: 13, fontWeight: '600', color: DARK },
});

const txStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 16,
    paddingVertical: 13,
    backgroundColor: '#FFFFFF',
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
  },
  dirDot: { width: 8, height: 8, borderRadius: 4, flexShrink: 0 },
  dotCredit: { backgroundColor: '#059669' },
  dotDebit: { backgroundColor: '#DC2626' },
  rowBody: { flex: 1 },
  txType: { fontSize: 13, fontWeight: '600', color: DARK, textTransform: 'capitalize' },
  narrative: { fontSize: 12, color: MUTED, marginTop: 2 },
  timestamp: { fontSize: 11, color: '#9CA3AF', marginTop: 3 },
  amount: { fontSize: 14, fontWeight: '700' },
  amountCredit: { color: '#059669' },
  amountDebit: { color: '#DC2626' },
});
