import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  FlatList,
  ScrollView,
  RefreshControl,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import Animated from 'react-native-reanimated';
import { useSusuGroups } from '../../hooks/useSusuGroups';
import { SusuCard } from '../../components/susu/SusuCard';
import { Icon, PressableScale, fadeInUp } from '../../components/ui';
import { colors, radii, shadows, spacing, typography } from '../../theme';
import type { RootStackParamList } from '../../navigation/RootNavigator';

type Tab = 'active' | 'past';

function EmptyState() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  return (
    <Animated.View entering={fadeInUp(60)} style={styles.emptyContainer} testID="empty-state">
      <View style={styles.emptyIconBadge}>
        <Icon name="people-circle-outline" size={30} color={colors.gold.text} />
      </View>
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
    </Animated.View>
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
        <View style={styles.tabBar}>
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
      </View>

      {error && <Text style={styles.error}>{error}</Text>}

      {/* List or empty state */}
      {displayed.length === 0 && tab === 'active' && !loading ? (
        <ScrollView
          contentContainerStyle={styles.emptyScroll}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={refresh}
              tintColor={colors.gold.base}
              colors={[colors.gold.base]}
            />
          }
        >
          <EmptyState />
        </ScrollView>
      ) : (
        <FlatList
          data={displayed}
          keyExtractor={g => g.group_id}
          renderItem={({ item, index }) => (
            <Animated.View entering={fadeInUp(index * 60)}>
              <SusuCard
                group={item}
                onPress={() => navigation.navigate('SusuDetail', { groupId: item.group_id })}
              />
            </Animated.View>
          )}
          contentContainerStyle={styles.list}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={refresh}
              tintColor={colors.gold.base}
              colors={[colors.gold.base]}
            />
          }
          ListEmptyComponent={
            <View style={styles.pastEmpty}>
              <View style={styles.emptyIconBadge}>
                <Icon name="people-outline" size={26} color={colors.gold.text} />
              </View>
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
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
    backgroundColor: colors.background,
  },
  tabRow: { paddingHorizontal: spacing.lg, paddingTop: spacing.lg, paddingBottom: spacing.sm },
  tabBar: {
    flexDirection: 'row',
    backgroundColor: colors.neutral[100],
    borderRadius: radii.pill,
    padding: spacing.xs,
  },
  tab: {
    flex: 1,
    paddingVertical: spacing.sm,
    alignItems: 'center',
    borderRadius: radii.pill,
  },
  tabActive: { backgroundColor: colors.surface, ...shadows.sm },
  tabText: { ...typography.caption, color: colors.textTertiary, fontWeight: '600' },
  tabTextActive: { color: colors.textPrimary, fontWeight: '700' },
  list: { padding: spacing.lg, gap: spacing.md },
  emptyScroll: { flexGrow: 1, justifyContent: 'center', padding: spacing.lg },
  error: {
    color: colors.status.error,
    fontSize: 13,
    paddingHorizontal: spacing.lg,
    marginTop: spacing.sm,
  },
  emptyContainer: {
    alignItems: 'center',
    padding: spacing['3xl'],
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    ...shadows.sm,
  },
  emptyIconBadge: {
    width: 64,
    height: 64,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.lg,
  },
  emptyTitle: { ...typography.h2, color: colors.textPrimary, marginBottom: spacing.sm },
  emptySubtitle: {
    ...typography.body,
    color: colors.textSecondary,
    textAlign: 'center',
    marginBottom: spacing['2xl'],
  },
  emptyCtaRow: { flexDirection: 'row', gap: spacing.md, alignSelf: 'stretch' },
  emptyBtn: { flex: 1, paddingVertical: spacing.md, borderRadius: radii.md, alignItems: 'center' },
  emptyBtnPrimary: { backgroundColor: colors.gold.base, ...shadows.sm },
  emptyBtnPrimaryText: { ...typography.button, fontSize: 15, color: colors.neutral[900] },
  emptyBtnSecondary: { backgroundColor: colors.neutral[100] },
  emptyBtnSecondaryText: { ...typography.button, fontSize: 15, color: colors.textPrimary },
  pastEmpty: { alignItems: 'center', paddingTop: spacing['4xl'], paddingHorizontal: spacing.lg },
});
