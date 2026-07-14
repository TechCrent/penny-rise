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
import Animated from 'react-native-reanimated';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { useVaults } from '../../hooks/useVaults';
import { VaultCard } from '../../components/VaultCard';
import { Banner, PressableScale, EmptyState, ScreenHeader, fadeInUp } from '../../components/ui';
import { colors, radii, shadows, spacing, typography } from '../../theme';
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
      <View style={styles.headerWrap}>
        <ScreenHeader
          title="Your vaults"
          onBack={() => navigation.goBack()}
          right={
            <TouchableOpacity
              onPress={() => navigation.navigate('CreateVault')}
              style={styles.createButton}
              accessibilityRole="button"
              accessibilityLabel="Create new vault"
            >
              <Text style={styles.createText}>+ New</Text>
            </TouchableOpacity>
          }
        />
      </View>

      {error && activeVaults.length === 0 ? (
        <Animated.View entering={fadeInUp(40)} style={styles.center}>
          <Banner tone="error" message="Could not load vaults." />
          <PressableScale onPress={() => refetch()} style={styles.retryButton}>
            <Text style={styles.retryText}>Retry</Text>
          </PressableScale>
        </Animated.View>
      ) : (
        <Animated.View entering={fadeInUp(40)} style={styles.flex}>
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
        </Animated.View>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  flex: { flex: 1 },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
  },
  headerWrap: {
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
    paddingBottom: spacing.xs,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  createButton: { minWidth: 64, alignItems: 'flex-end' },
  createText: { ...typography.button, fontSize: 15, color: colors.gold.text },
  list: { paddingHorizontal: spacing.lg, paddingTop: spacing.md, paddingBottom: spacing['3xl'] },
  retryButton: {
    backgroundColor: colors.gold.base,
    paddingHorizontal: spacing.xl,
    paddingVertical: spacing.sm,
    borderRadius: radii.md,
    ...shadows.sm,
  },
  retryText: { color: colors.neutral[900], fontWeight: '600' },
  emptyButton: {
    alignSelf: 'center',
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingHorizontal: spacing.xl,
    paddingVertical: spacing.md,
    marginTop: -spacing.md,
    ...shadows.sm,
  },
  emptyButtonText: { color: colors.neutral[900], fontWeight: '700' },
});
