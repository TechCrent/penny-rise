import React, { useCallback } from 'react';
import { View, Text, FlatList, TouchableOpacity, RefreshControl, StyleSheet, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { useVaults } from '../../hooks/useVaults';
import { VaultCard } from '../../components/VaultCard';
import { PressableScale, EmptyState } from '../../components/ui';
import { colors, radii, spacing, typography } from '../../theme';
import type { VaultListItem } from '../../api/vaults';

type Nav = NativeStackNavigationProp<RootStackParamList, 'VaultList'>;

export default function VaultListScreen() {
  const navigation = useNavigation<Nav>();
  const { data, isLoading, isFetching, error, refetch } = useVaults();

  const onRefresh = useCallback(() => {
    refetch();
  }, [refetch]);

  const activeVaults = (data?.vaults ?? []).filter(
    v => v.status === 'ACTIVE' || v.status === 'EARLY_EXIT_PENDING',
  );

  const renderVault = ({ item }: { item: VaultListItem }) => (
    <VaultCard
      vault={item}
      onPress={() => navigation.navigate('VaultDetail', { vaultId: item.id })}
    />
  );

  if (isLoading) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.center}>
          <ActivityIndicator size="large" color={colors.gold.base} />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <Text style={styles.backText}>← Back</Text>
        </TouchableOpacity>
        <Text style={styles.title}>Your vaults</Text>
        <TouchableOpacity
          onPress={() => navigation.navigate('CreateVault')}
          style={styles.createButton}
        >
          <Text style={styles.createText}>+ New</Text>
        </TouchableOpacity>
      </View>

      {error && activeVaults.length === 0 ? (
        <View style={styles.center}>
          <Text style={styles.errorText}>Could not load vaults.</Text>
          <PressableScale onPress={() => refetch()} style={styles.retryButton}>
            <Text style={styles.retryText}>Retry</Text>
          </PressableScale>
        </View>
      ) : (
        <FlatList
          data={activeVaults}
          keyExtractor={item => item.id}
          renderItem={renderVault}
          contentContainerStyle={styles.list}
          refreshControl={
            <RefreshControl
              refreshing={isFetching && !isLoading}
              onRefresh={onRefresh}
              tintColor={colors.gold.base}
              colors={[colors.gold.base]}
            />
          }
          ListEmptyComponent={
            <EmptyState
              icon="lock-closed-outline"
              title="No vaults yet"
              message="Create a vault to start saving toward a goal."
            />
          }
          ListFooterComponent={
            activeVaults.length === 0 ? (
              <PressableScale
                style={styles.emptyButton}
                onPress={() => navigation.navigate('CreateVault')}
              >
                <Text style={styles.emptyButtonText}>Create vault</Text>
              </PressableScale>
            ) : null
          }
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
  },
  backButton: { minWidth: 64 },
  backText: { fontSize: 15, color: colors.textPrimary, fontWeight: '600' },
  title: { ...typography.h3, color: colors.textPrimary },
  createButton: { minWidth: 64, alignItems: 'flex-end' },
  createText: { fontSize: 15, color: colors.textPrimary, fontWeight: '700' },
  list: { paddingHorizontal: spacing.lg, paddingBottom: spacing['3xl'] },
  errorText: { color: colors.status.error, fontSize: 14, marginBottom: spacing.md },
  retryButton: {
    backgroundColor: colors.gold.base,
    paddingHorizontal: spacing.xl,
    paddingVertical: spacing.sm,
    borderRadius: radii.sm,
  },
  retryText: { color: colors.neutral[900], fontWeight: '600' },
  emptyButton: {
    alignSelf: 'center',
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingHorizontal: spacing.xl,
    paddingVertical: spacing.md,
    marginTop: -spacing.md,
  },
  emptyButtonText: { color: colors.neutral[900], fontWeight: '700' },
});
