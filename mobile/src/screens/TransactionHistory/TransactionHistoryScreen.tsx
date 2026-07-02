import React, { useCallback, useState } from 'react';
import { FlatList, View, ActivityIndicator, RefreshControl, StyleSheet } from 'react-native';
import { FilterTabs } from './components/FilterTabs';
import { TransactionListItem } from './components/TransactionListItem';
import { TransactionEmptyState } from './components/TransactionEmptyState';
import { ReceiptModal } from './components/ReceiptModal';
import { useTransactionHistory } from './useTransactionHistory';
import type { FilterTab, UnifiedTransactionItem } from './types';

export function TransactionHistoryScreen() {
  const [activeTab, setActiveTab] = useState<FilterTab>('ALL');
  const [selectedTransaction, setSelectedTransaction] = useState<UnifiedTransactionItem | null>(
    null,
  );

  const {
    transactions,
    isLoading,
    hasNextPage,
    isFetchingNextPage,
    fetchNextPage,
    isRefetching,
    refetch,
  } = useTransactionHistory(activeTab);

  const handleLoadMore = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) fetchNextPage();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  return (
    <View style={styles.container} testID="transaction-history-screen">
      <FilterTabs activeTab={activeTab} onTabChange={setActiveTab} />

      {isLoading ? (
        <View style={styles.centered}>
          <ActivityIndicator testID="history-loading" />
        </View>
      ) : (
        <FlatList
          data={transactions}
          keyExtractor={item => item.transactionReference}
          renderItem={({ item }) => (
            <TransactionListItem item={item} onPress={setSelectedTransaction} />
          )}
          ListEmptyComponent={<TransactionEmptyState activeTab={activeTab} />}
          onEndReached={handleLoadMore}
          onEndReachedThreshold={0.3}
          refreshControl={<RefreshControl refreshing={isRefetching} onRefresh={refetch} />}
          ListFooterComponent={
            isFetchingNextPage ? (
              <ActivityIndicator style={styles.footerSpinner} testID="load-more-spinner" />
            ) : null
          }
          ItemSeparatorComponent={() => <View style={styles.separator} />}
          contentContainerStyle={transactions.length === 0 ? styles.emptyContent : undefined}
        />
      )}

      <ReceiptModal
        transaction={selectedTransaction}
        onClose={() => setSelectedTransaction(null)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F9FAFB' },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  footerSpinner: { paddingVertical: 16 },
  separator: { height: StyleSheet.hairlineWidth, backgroundColor: '#E5E7EB', marginLeft: 16 },
  emptyContent: { flexGrow: 1 },
});
