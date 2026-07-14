import React, { useEffect, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  Pressable,
  RefreshControl,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { LinearGradient } from 'expo-linear-gradient';
import Animated from 'react-native-reanimated';

import { useWalletBalance } from '../../hooks/useWalletBalance';
import { useWalletStatement } from '../../hooks/useWalletStatement';
import { ActivityRow } from '../../components/wallet/ActivityRow';
import { WalletSkeleton } from '../../components/wallet/WalletSkeleton';
import { QuickActionButton } from '../../components/wallet/QuickActionButton';
import { AnimatedNumber, EmptyState as UiEmptyState, fadeInUp } from '../../components/ui';
import { colors, spacing } from '../../theme';
import type { WalletActivity } from '../../types/wallet';

function formatWalletCedis(pesewas: number): string {
  return (pesewas / 100).toFixed(2);
}

function EmptyState() {
  return (
    <View style={styles.emptyStateWrap} testID="empty-state">
      <UiEmptyState
        icon="mail-open-outline"
        title="No activity yet"
        message="Deposit to your wallet or receive a transfer to get started."
      />
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
  } = useWalletStatement();

  const isFirstLoad = (balanceLoading || stmtLoading) && entries.length === 0 && !balance;

  useEffect(() => {
    fetchBalance();
    fetchStatement();
  }, [fetchBalance, fetchStatement]);

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
      <LinearGradient
        colors={[colors.heroFrom, colors.heroTo]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={styles.balanceCard}
        testID="balance-card"
      >
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
              GHS{' '}
              {balance ? (
                <AnimatedNumber value={balance.balancePesewas} formatter={formatWalletCedis} />
              ) : (
                '—'
              )}
            </Text>
            <Text style={styles.balanceAccountId} numberOfLines={1}>
              Wallet · {balance?.accountId?.slice(0, 8) ?? ''}
            </Text>
          </>
        )}
      </LinearGradient>

      <Animated.View entering={fadeInUp(60)} style={styles.quickActions}>
        <QuickActionButton
          icon="paper-plane-outline"
          label="Send"
          onPress={() => navigation.navigate('RecipientPicker')}
          testID="send-btn"
        />
        <QuickActionButton
          icon="arrow-up-circle-outline"
          label="Withdraw"
          onPress={() => navigation.navigate('WalletWithdrawComingSoon')}
          testID="withdraw-btn"
        />
      </Animated.View>

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Recent activity</Text>
        <Pressable
          onPress={() => navigation.navigate('TransactionHistory')}
          testID="see-all-transactions-btn"
        >
          <Text style={styles.sectionLink}>See all</Text>
        </Pressable>
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
              <ActivityIndicator size="small" color={colors.gold.base} />
            </View>
          ) : null
        }
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={handleRefresh}
            tintColor={colors.gold.base}
            colors={[colors.gold.base]}
          />
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
  screen: { flex: 1, backgroundColor: colors.background },
  listContent: { flexGrow: 1, paddingBottom: spacing['2xl'] },

  balanceCard: {
    paddingHorizontal: spacing.xl,
    paddingTop: spacing['4xl'],
    paddingBottom: spacing['2xl'],
  },
  balanceLabel: {
    fontSize: 13,
    color: colors.textOnDarkMuted,
    marginBottom: spacing.xs,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  balanceAmount: {
    fontSize: 44,
    fontWeight: '900',
    color: colors.textOnDark,
    marginBottom: spacing.xs,
  },
  balanceAccountId: { fontSize: 12, color: colors.textOnDarkFaint },
  balanceUnavailable: {
    fontSize: 24,
    fontWeight: '700',
    color: colors.textOnDarkMuted,
    marginBottom: spacing.xs,
  },
  balanceRetryLink: { color: colors.gold.base, fontSize: 13 },

  quickActions: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingVertical: spacing.xl,
    paddingHorizontal: spacing.lg,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.neutral[100],
  },

  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    backgroundColor: colors.background,
  },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: colors.neutral[700],
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  sectionLink: { fontSize: 13, fontWeight: '600', color: colors.textPrimary },

  errorBanner: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: colors.status.errorBg,
    marginHorizontal: spacing.lg,
    marginBottom: spacing.sm,
    borderRadius: 8,
    padding: spacing.md,
  },
  errorText: { color: colors.status.errorText, fontSize: 13, flex: 1 },
  retryLink: { color: colors.status.infoText, fontSize: 13, fontWeight: '600' },

  loadingMore: { padding: spacing.lg, alignItems: 'center' },

  emptyStateWrap: { padding: spacing.lg },
});
