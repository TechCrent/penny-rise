import React, { useCallback, useState } from 'react';
import {
  FlatList,
  View,
  Text,
  ActivityIndicator,
  RefreshControl,
  StyleSheet,
  TouchableOpacity,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { FilterTabs } from './components/FilterTabs';
import { TransactionListItem } from './components/TransactionListItem';
import { TransactionEmptyState } from './components/TransactionEmptyState';
import { TransactionHistorySkeleton } from './components/TransactionHistorySkeleton';
import { ReceiptModal } from './components/ReceiptModal';
import { useTransactionHistory } from './useTransactionHistory';
import { colors, spacing, typography } from '../../theme';
import type { FilterTab, UnifiedTransactionItem } from './types';

type Nav = NativeStackNavigationProp<RootStackParamList, 'TransactionHistory'>;
type Route = RouteProp<RootStackParamList, 'TransactionHistory'>;

const TITLE_BY_SCOPE = {
  vault: 'Vault activity',
  wallet: 'Wallet activity',
} as const;

export function TransactionHistoryScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const scope = route.params?.scope;

  const [activeTab, setActiveTab] = useState<FilterTab>('ALL');
  const [selectedTransaction, setSelectedTransaction] = useState<UnifiedTransactionItem | null>(
    null,
  );

  const {
    transactions,
    isLoading,
    isError,
    hasNextPage,
    isFetchingNextPage,
    fetchNextPage,
    isRefetching,
    refetch,
  } = useTransactionHistory(activeTab, scope);

  const handleLoadMore = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) fetchNextPage();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  return (
    <SafeAreaView style={styles.container} testID="transaction-history-screen">
      <View style={styles.header}>
        <TouchableOpacity
          style={styles.headerBtn}
          onPress={() => navigation.goBack()}
          accessibilityRole="button"
          accessibilityLabel="Go back"
        >
          <Text style={styles.headerBtnIcon}>←</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{scope ? TITLE_BY_SCOPE[scope] : 'History'}</Text>
        <View style={styles.headerBtn} />
      </View>

      <FilterTabs activeTab={activeTab} onTabChange={setActiveTab} />

      {isLoading ? (
        <TransactionHistorySkeleton />
      ) : (
        <FlatList
          data={transactions}
          keyExtractor={item => item.transactionReference}
          renderItem={({ item }) => (
            <TransactionListItem item={item} onPress={setSelectedTransaction} />
          )}
          ListEmptyComponent={
            isError ? (
              <View style={styles.centered} testID="history-error">
                <Text style={styles.message}>
                  Couldn&apos;t load transactions. Pull down to try again.
                </Text>
              </View>
            ) : (
              <TransactionEmptyState activeTab={activeTab} />
            )
          }
          onEndReached={handleLoadMore}
          onEndReachedThreshold={0.3}
          refreshControl={
            <RefreshControl
              refreshing={isRefetching}
              onRefresh={refetch}
              tintColor={colors.gold.base}
              colors={[colors.gold.base]}
            />
          }
          ListFooterComponent={
            isFetchingNextPage ? (
              <ActivityIndicator
                style={styles.footerSpinner}
                color={colors.gold.base}
                testID="load-more-spinner"
              />
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
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
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
  headerBtn: { width: 40, height: 40, justifyContent: 'center' },
  headerBtnIcon: { fontSize: 22, color: colors.textPrimary },
  headerTitle: { ...typography.h3, color: colors.textPrimary },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing['3xl'] },
  message: { fontSize: 14, color: colors.textSecondary, textAlign: 'center' },
  footerSpinner: { paddingVertical: spacing.lg },
  separator: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: colors.border,
    marginLeft: spacing.lg,
  },
  emptyContent: { flexGrow: 1 },
});
