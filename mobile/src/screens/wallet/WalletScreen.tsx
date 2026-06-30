import React, { useEffect, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  RefreshControl,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';

import { useWalletBalance } from '../../hooks/useWalletBalance';
import { useWalletStatement } from '../../hooks/useWalletStatement';
import { ActivityRow } from '../../components/wallet/ActivityRow';
import { WalletSkeleton } from '../../components/wallet/WalletSkeleton';
import { QuickActionButton } from '../../components/wallet/QuickActionButton';
import type { WalletActivity } from '../../types/wallet';

function EmptyState() {
  return (
    <View style={styles.emptyState} testID="empty-state">
      <Text style={styles.emptyIcon}>📭</Text>
      <Text style={styles.emptyTitle}>No activity yet</Text>
      <Text style={styles.emptySubtitle}>
        Deposit to your wallet or receive a transfer to get started.
      </Text>
    </View>
  );
}

function ErrorBanner({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <View style={styles.errorBanner} testID="error-banner">
      <Text style={styles.errorText}>{message}</Text>
      <TouchableOpacity onPress={onRetry} testID="retry-btn">
        <Text style={styles.retryLink}>Retry</Text>
      </TouchableOpacity>
    </View>
  );
}

export function WalletScreen() {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const navigation = useNavigation<any>();

  const {
    balance,
    loading: balanceLoading,
    error: balanceError,
    fetch: fetchBalance,
  } = useWalletBalance();

  const {
    entries,
    loading: stmtLoading,
    loadingMore,
    refreshing,
    error: stmtError,
    hasMore,
    fetch: fetchStatement,
    loadMore,
    refresh,
  } = useWalletStatement(balance?.accountId ?? null);

  const isFirstLoad = (balanceLoading || stmtLoading) && entries.length === 0 && !balance;

  useEffect(() => {
    fetchBalance();
  }, [fetchBalance]);

  useEffect(() => {
    if (balance?.accountId) {
      fetchStatement();
    }
  }, [balance?.accountId, fetchStatement]);

  const handleRefresh = useCallback(async () => {
    await fetchBalance();
    refresh();
  }, [fetchBalance, refresh]);

  const handleEndReached = useCallback(() => {
    if (hasMore && !loadingMore) loadMore();
  }, [hasMore, loadingMore, loadMore]);

  if (isFirstLoad) {
    return (
      <View style={styles.screen}>
        <WalletSkeleton />
      </View>
    );
  }

  const ListHeader = (
    <>
      <View style={styles.balanceCard} testID="balance-card">
        <Text style={styles.balanceLabel}>Available balance</Text>
        {balanceError ? (
          <View testID="balance-error">
            <Text style={styles.balanceUnavailable}>Balance unavailable</Text>
            <TouchableOpacity onPress={fetchBalance} testID="balance-retry-btn">
              <Text style={styles.balanceRetryLink}>Tap to retry</Text>
            </TouchableOpacity>
          </View>
        ) : (
          <>
            <Text style={styles.balanceAmount} testID="balance-amount">
              GHS {balance?.balanceCedis ?? '—'}
            </Text>
            <Text style={styles.balanceAccountId} numberOfLines={1}>
              Wallet · {balance?.accountId?.slice(0, 8) ?? ''}
            </Text>
          </>
        )}
      </View>

      <View style={styles.quickActions}>
        <QuickActionButton
          icon="⬇"
          label="Deposit"
          onPress={() => navigation.navigate('Deposit')}
          testID="deposit-btn"
        />
        <QuickActionButton
          icon="↗️"
          label="Send"
          onPress={() => navigation.navigate('RecipientPicker')}
          testID="send-btn"
        />
        <QuickActionButton
          icon="🏦"
          label="To vault"
          onPress={() => navigation.navigate('MoveToVault')}
          testID="vault-btn"
        />
      </View>

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Recent activity</Text>
      </View>

      {stmtError && <ErrorBanner message={stmtError} onRetry={() => fetchStatement()} />}
    </>
  );

  return (
    <View style={styles.screen} testID="wallet-screen">
      <FlatList<WalletActivity>
        data={entries}
        keyExtractor={item => item.id}
        renderItem={({ item }) => <ActivityRow item={item} />}
        ListHeaderComponent={ListHeader}
        ListEmptyComponent={!stmtLoading && !stmtError ? <EmptyState /> : null}
        ListFooterComponent={
          loadingMore ? (
            <View style={styles.loadingMore} testID="loading-more">
              <ActivityIndicator size="small" color="#6B7280" />
            </View>
          ) : null
        }
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} tintColor="#111827" />
        }
        onEndReached={handleEndReached}
        onEndReachedThreshold={0.3}
        contentContainerStyle={styles.listContent}
        testID="activity-list"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F9FAFB' },
  listContent: { flexGrow: 1, paddingBottom: 32 },

  balanceCard: {
    backgroundColor: '#111827',
    paddingHorizontal: 24,
    paddingTop: 40,
    paddingBottom: 28,
  },
  balanceLabel: {
    fontSize: 13,
    color: '#9CA3AF',
    marginBottom: 6,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  balanceAmount: { fontSize: 44, fontWeight: '900', color: '#FFFFFF', marginBottom: 4 },
  balanceAccountId: { fontSize: 12, color: '#4B5563' },
  balanceUnavailable: { fontSize: 24, fontWeight: '700', color: '#6B7280', marginBottom: 4 },
  balanceRetryLink: { color: '#60A5FA', fontSize: 13 },

  quickActions: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingVertical: 20,
    paddingHorizontal: 16,
    backgroundColor: '#FFFFFF',
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
  },

  sectionHeader: { paddingHorizontal: 16, paddingVertical: 12, backgroundColor: '#F9FAFB' },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: '#374151',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },

  errorBanner: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#FEF2F2',
    marginHorizontal: 16,
    marginBottom: 8,
    borderRadius: 8,
    padding: 12,
  },
  errorText: { color: '#991B1B', fontSize: 13, flex: 1 },
  retryLink: { color: '#1D4ED8', fontSize: 13, fontWeight: '600' },

  loadingMore: { padding: 16, alignItems: 'center' },

  emptyState: { alignItems: 'center', padding: 48 },
  emptyIcon: { fontSize: 48, marginBottom: 12 },
  emptyTitle: { fontSize: 18, fontWeight: '700', color: '#111827', marginBottom: 6 },
  emptySubtitle: { fontSize: 14, color: '#6B7280', textAlign: 'center' },
});
