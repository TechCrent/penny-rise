import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  FlatList,
  ScrollView,
  TouchableOpacity,
  RefreshControl,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useSusuGroups } from '../../hooks/useSusuGroups';
import { SusuCard } from '../../components/susu/SusuCard';
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
        <TouchableOpacity
          style={[styles.emptyBtn, styles.emptyBtnPrimary]}
          onPress={() => navigation.navigate('CreateSusu')}
          testID="create-susu-cta"
        >
          <Text style={styles.emptyBtnPrimaryText}>Create a susu</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.emptyBtn, styles.emptyBtnSecondary]}
          onPress={() => navigation.navigate('JoinSusu')}
          testID="join-susu-cta"
        >
          <Text style={styles.emptyBtnSecondaryText}>Join with code</Text>
        </TouchableOpacity>
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
        <ActivityIndicator size="large" color="#111827" />
      </View>
    );
  }

  return (
    <View style={styles.screen}>
      {/* Tab toggle */}
      <View style={styles.tabRow}>
        <TouchableOpacity
          style={[styles.tab, tab === 'active' && styles.tabActive]}
          onPress={() => setTab('active')}
          testID="tab-active"
        >
          <Text style={[styles.tabText, tab === 'active' && styles.tabTextActive]}>
            Active ({activeGroups.length})
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tab, tab === 'past' && styles.tabActive]}
          onPress={() => setTab('past')}
          testID="tab-past"
        >
          <Text style={[styles.tabText, tab === 'past' && styles.tabTextActive]}>
            Past ({pastGroups.length})
          </Text>
        </TouchableOpacity>
      </View>

      {error && <Text style={styles.error}>{error}</Text>}

      {/* List or empty state */}
      {displayed.length === 0 && tab === 'active' && !loading ? (
        <ScrollView refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
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
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
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
  screen: { flex: 1, backgroundColor: '#F9FAFB' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  tabRow: { flexDirection: 'row', padding: 16, paddingBottom: 0 },
  tab: {
    flex: 1,
    paddingVertical: 10,
    alignItems: 'center',
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
  },
  tabActive: { borderBottomColor: '#111827' },
  tabText: { fontSize: 14, color: '#9CA3AF', fontWeight: '500' },
  tabTextActive: { color: '#111827', fontWeight: '700' },
  list: { padding: 16 },
  error: { color: '#EF4444', fontSize: 13, paddingHorizontal: 16, marginTop: 8 },
  emptyContainer: { alignItems: 'center', padding: 32 },
  emptyTitle: { fontSize: 20, fontWeight: '700', color: '#111827', marginBottom: 8 },
  emptySubtitle: { fontSize: 14, color: '#6B7280', textAlign: 'center', marginBottom: 24 },
  emptyCtaRow: { flexDirection: 'row', gap: 12 },
  emptyBtn: { flex: 1, paddingVertical: 14, borderRadius: 12, alignItems: 'center' },
  emptyBtnPrimary: { backgroundColor: '#111827' },
  emptyBtnPrimaryText: { color: '#FFFFFF', fontWeight: '700' },
  emptyBtnSecondary: { backgroundColor: '#F3F4F6' },
  emptyBtnSecondaryText: { color: '#111827', fontWeight: '700' },
});
