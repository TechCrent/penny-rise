import React, { useEffect, useState } from 'react';
import { View, Text, FlatList, ScrollView, RefreshControl, StyleSheet, ActivityIndicator } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useSusuGroups } from '../../hooks/useSusuGroups';
import { SusuCard } from '../../components/susu/SusuCard';
import { PressableScale } from '../../components/ui';
import { colors, radii, spacing, typography } from '../../theme';
import type { RootStackParamList } from '../../navigation/RootNavigator';

type Tab = 'active' | 'past';

function EmptyState() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  return (
    <View style={styles.emptyContainer} testID="empty-state">
      <Text style={styles.emptyTitle}>No susus yet</Text>
      <Text style={styles.emptySubtitle}>
        Start a rotating savings group or join one with a code.
      </Text>
      <View style={styles.emptyCtaRow}>
        <PressableScale
          style={[styles.emptyBtn, styles.emptyBtnPrimary]}
          onPress={() => navigation.navigate('CreateSusu')}
          testID="create-susu-cta"
        >
          <Text style={styles.emptyBtnPrimaryText}>Create a susu</Text>
        </PressableScale>
        <PressableScale
          style={[styles.emptyBtn, styles.emptyBtnSecondary]}
          onPress={() => navigation.navigate('JoinSusu')}
          testID="join-susu-cta"
        >
          <Text style={styles.emptyBtnSecondaryText}>Join with code</Text>
        </PressableScale>
      </View>
    </View>
  );
}

export function SusuListScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const { groups, loading, refreshing, error, fetch, refresh } = useSusuGroups();
  const [tab, setTab] = useState<Tab>('active');

  useEffect(() => {
    fetch();
  }, [fetch]);

  const activeGroups = groups.filter(g => g.status === 'PENDING' || g.status === 'ACTIVE');
  const pastGroups = groups.filter(g => g.status === 'COMPLETED' || g.status === 'CANCELLED');
  const displayed = tab === 'active' ? activeGroups : pastGroups;

  if (loading && groups.length === 0) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color={colors.gold.base} />
      </View>
    );
  }

  return (
    <View style={styles.screen}>
      {/* Tab toggle */}
      <View style={styles.tabRow}>
        <PressableScale
          style={[styles.tab, tab === 'active' && styles.tabActive]}
          onPress={() => setTab('active')}
          testID="tab-active"
        >
          <Text style={[styles.tabText, tab === 'active' && styles.tabTextActive]}>
            Active ({activeGroups.length})
          </Text>
        </PressableScale>
        <PressableScale
          style={[styles.tab, tab === 'past' && styles.tabActive]}
          onPress={() => setTab('past')}
          testID="tab-past"
        >
          <Text style={[styles.tabText, tab === 'past' && styles.tabTextActive]}>
            Past ({pastGroups.length})
          </Text>
        </PressableScale>
      </View>

      {error && <Text style={styles.error}>{error}</Text>}

      {/* List or empty state */}
      {displayed.length === 0 && tab === 'active' && !loading ? (
        <ScrollView
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={refresh} tintColor={colors.gold.base} colors={[colors.gold.base]} />
          }
        >
          <EmptyState />
        </ScrollView>
      ) : (
        <FlatList
          data={displayed}
          keyExtractor={g => g.group_id}
          renderItem={({ item }) => (
            <SusuCard
              group={item}
              onPress={() => navigation.navigate('SusuDetail', { groupId: item.group_id })}
            />
          )}
          contentContainerStyle={styles.list}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={refresh} tintColor={colors.gold.base} colors={[colors.gold.base]} />
          }
          ListEmptyComponent={
            <View style={styles.center}>
              <Text style={styles.emptySubtitle}>No past susus yet.</Text>
            </View>
          }
          testID="susu-list"
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl },
  tabRow: { flexDirection: 'row', padding: spacing.lg, paddingBottom: 0 },
  tab: {
    flex: 1,
    paddingVertical: spacing.sm,
    alignItems: 'center',
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
  },
  tabActive: { borderBottomColor: colors.gold.base },
  tabText: { fontSize: 14, color: colors.textTertiary, fontWeight: '500' },
  tabTextActive: { color: colors.textPrimary, fontWeight: '700' },
  list: { padding: spacing.lg },
  error: { color: colors.status.error, fontSize: 13, paddingHorizontal: spacing.lg, marginTop: spacing.sm },
  emptyContainer: { alignItems: 'center', padding: spacing['3xl'] },
  emptyTitle: { ...typography.h2, color: colors.textPrimary, marginBottom: spacing.sm },
  emptySubtitle: { fontSize: 14, color: colors.textSecondary, textAlign: 'center', marginBottom: spacing['2xl'] },
  emptyCtaRow: { flexDirection: 'row', gap: spacing.md },
  emptyBtn: { flex: 1, paddingVertical: spacing.md, borderRadius: radii.md, alignItems: 'center' },
  emptyBtnPrimary: { backgroundColor: colors.gold.base },
  emptyBtnPrimaryText: { color: colors.neutral[900], fontWeight: '700' },
  emptyBtnSecondary: { backgroundColor: colors.neutral[100] },
  emptyBtnSecondaryText: { color: colors.textPrimary, fontWeight: '700' },
});
