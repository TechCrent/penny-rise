import React, { useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  RefreshControl,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { useVaults } from '../../hooks/useVaults';
import { VaultCard } from '../../components/VaultCard';
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
          <ActivityIndicator size="large" color="#111827" />
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
          <TouchableOpacity onPress={() => refetch()} style={styles.retryButton}>
            <Text style={styles.retryText}>Retry</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <FlatList
          data={activeVaults}
          keyExtractor={item => item.id}
          renderItem={renderVault}
          contentContainerStyle={styles.list}
          refreshControl={
            <RefreshControl refreshing={isFetching && !isLoading} onRefresh={onRefresh} />
          }
          ListEmptyComponent={
            <View style={styles.emptyState}>
              <Text style={styles.emptyHeading}>No vaults yet</Text>
              <Text style={styles.emptyBody}>Create a vault to start saving toward a goal.</Text>
              <TouchableOpacity
                style={styles.emptyButton}
                onPress={() => navigation.navigate('CreateVault')}
              >
                <Text style={styles.emptyButtonText}>Create vault</Text>
              </TouchableOpacity>
            </View>
          }
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F9FAFB' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  backButton: { minWidth: 64 },
  backText: { fontSize: 15, color: '#1A1A1A', fontWeight: '600' },
  title: { fontSize: 17, fontWeight: '700', color: '#111827' },
  createButton: { minWidth: 64, alignItems: 'flex-end' },
  createText: { fontSize: 15, color: '#1A1A1A', fontWeight: '700' },
  list: { paddingHorizontal: 16, paddingBottom: 32 },
  errorText: { color: '#EF4444', fontSize: 14, marginBottom: 12 },
  retryButton: {
    backgroundColor: '#111827',
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 8,
  },
  retryText: { color: '#FFFFFF', fontWeight: '600' },
  emptyState: { alignItems: 'center', paddingVertical: 48, paddingHorizontal: 24 },
  emptyHeading: { fontSize: 18, fontWeight: '700', color: '#111827', marginBottom: 8 },
  emptyBody: { fontSize: 14, color: '#6B7280', textAlign: 'center', marginBottom: 20 },
  emptyButton: {
    backgroundColor: '#1A1A1A',
    borderRadius: 10,
    paddingHorizontal: 24,
    paddingVertical: 12,
  },
  emptyButtonText: { color: '#FFFFFF', fontWeight: '700' },
});
