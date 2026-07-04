import React, { useCallback } from 'react';
import {
  View,
  Text,
  ScrollView,
  RefreshControl,
  TouchableOpacity,
  StyleSheet,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../navigation/RootNavigator';
import { useAuth } from '../auth/AuthContext';
import { useKycResumability } from '../hooks/useKycResumability';
import { useVaults } from '../hooks/useVaults';
import { useUnreadNotificationsCount } from '../features/notifications/useNotificationsList';
import { VaultCard } from '../components/VaultCard';
import { HomeSkeleton } from '../components/HomeSkeleton';
import type { VaultListItem } from '../api/vaults';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Home'>;

const QUICK_ACTIONS = [
  { id: 'deposit', label: 'Deposit', icon: '↓', screen: 'VaultList' as const },
  { id: 'send', label: 'Send', icon: '→', screen: 'RecipientPicker' as const },
  { id: 'withdraw', label: 'Withdraw', icon: '↑', screen: 'VaultList' as const },
  { id: 'challenges', label: 'Challenges', icon: '🏆', screen: 'ChallengesList' as const },
];

function computeTotalPesewas(vaults: VaultListItem[]): number | null {
  const available = vaults.filter(v => v.balance_pesewas !== null);
  if (available.length === 0) return null;
  return available.reduce((sum, v) => sum + (v.balance_pesewas ?? 0), 0);
}

function formatCedis(pesewas: number): string {
  return (pesewas / 100).toLocaleString('en-GH', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}

export default function HomeScreen() {
  const navigation = useNavigation<Nav>();
  const { kycStatus } = useAuth();
  useKycResumability({ enabled: kycStatus !== 'APPROVED' });

  const { data, isLoading, isFetching, error, refetch } = useVaults();
  const unreadNotificationsCount = useUnreadNotificationsCount();

  const onRefresh = useCallback(() => {
    refetch();
  }, [refetch]);

  const activeVaults = (data?.vaults ?? []).filter(
    v => v.status === 'ACTIVE' || v.status === 'EARLY_EXIT_PENDING',
  );
  const previewVaults = activeVaults.slice(0, 2);
  const totalPesewas = data ? computeTotalPesewas(data.vaults) : null;
  const hasVaults = activeVaults.length > 0;

  if (isLoading) {
    return (
      <SafeAreaView style={styles.safe}>
        <HomeSkeleton />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl refreshing={isFetching && !isLoading} onRefresh={onRefresh} />
        }
      >
        <View style={styles.header}>
          <View>
            <Text style={styles.greeting}>{greeting()}</Text>
            <Text style={styles.subhead}>Here&apos;s how your savings are doing</Text>
          </View>
          <View style={styles.bellButton} accessibilityLabel="Notifications">
            <Text style={styles.bellIcon}>🔔</Text>
          </View>
        </View>

        <View style={styles.heroCard}>
          <Text style={styles.heroLabel}>Total savings</Text>
          {totalPesewas === null ? (
            <Text style={styles.heroBalanceUnavailable}>
              {error ? 'Could not load balances' : '—'}
            </Text>
          ) : (
            <View style={styles.heroBalanceRow}>
              <Text style={styles.heroCurrency}>GHS </Text>
              <Text style={styles.heroBalance}>{formatCedis(totalPesewas)}</Text>
            </View>
          )}
          <Text style={styles.heroSub}>
            Across {data?.total_count ?? 0} vault{(data?.total_count ?? 0) !== 1 ? 's' : ''}
            {data?.balance_unavailable_count
              ? ` · ${data.balance_unavailable_count} balance${
                  data.balance_unavailable_count > 1 ? 's' : ''
                } unavailable`
              : ''}
          </Text>
        </View>

        <View style={styles.tileRow}>
          {QUICK_ACTIONS.map(action => (
            <TouchableOpacity
              key={action.id}
              style={styles.tile}
              onPress={() => navigation.navigate(action.screen)}
              activeOpacity={0.8}
              accessibilityRole="button"
              accessibilityLabel={action.label}
            >
              <Text style={styles.tileIcon}>{action.icon}</Text>
              <Text style={styles.tileLabel}>{action.label}</Text>
            </TouchableOpacity>
          ))}
        </View>

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Your vaults</Text>
          {hasVaults && (
            <TouchableOpacity onPress={() => navigation.navigate('VaultList')}>
              <Text style={styles.seeAll}>See all</Text>
            </TouchableOpacity>
          )}
        </View>

        {hasVaults ? (
          <>
            {previewVaults.map(vault => (
              <VaultCard
                key={vault.id}
                vault={vault}
                onPress={() => navigation.navigate('VaultDetail', { vaultId: vault.id })}
              />
            ))}
            {activeVaults.length > 2 && (
              <TouchableOpacity
                style={styles.seeAllCard}
                onPress={() => navigation.navigate('VaultList')}
                activeOpacity={0.8}
              >
                <Text style={styles.seeAllCardText}>
                  +{activeVaults.length - 2} more vault{activeVaults.length - 2 > 1 ? 's' : ''} ·
                  See all
                </Text>
              </TouchableOpacity>
            )}
          </>
        ) : (
          <View style={styles.emptyState}>
            <Text style={styles.emptyIllustration}>🏦</Text>
            <Text style={styles.emptyHeading}>No vaults yet</Text>
            <Text style={styles.emptyBody}>
              Vaults help you save toward specific goals — a school fund, an emergency stash, or
              anything you&apos;re working toward.
            </Text>
            <TouchableOpacity
              style={styles.emptyButton}
              onPress={() => navigation.navigate('CreateVault')}
              activeOpacity={0.85}
            >
              <Text style={styles.emptyButtonText}>Create your first vault</Text>
            </TouchableOpacity>
          </View>
        )}

        {hasVaults && (
          <TouchableOpacity
            style={styles.fab}
            onPress={() => navigation.navigate('CreateVault')}
            activeOpacity={0.88}
            accessibilityRole="button"
            accessibilityLabel="Create new vault"
          >
            <Text style={styles.fabIcon}>＋</Text>
          </TouchableOpacity>
        )}

        <TouchableOpacity
          style={styles.deleteAccountLink}
          onPress={() => navigation.navigate('DeleteAccount')}
          accessibilityRole="button"
          accessibilityLabel="Delete account"
        >
          <Text style={styles.deleteAccountLinkText}>Delete account</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: '#F9FAFB',
  },
  scroll: {
    flex: 1,
  },
  content: {
    paddingHorizontal: 16,
    paddingTop: Platform.OS === 'android' ? 16 : 12,
    paddingBottom: 100,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 20,
  },
  greeting: {
    fontSize: 22,
    fontWeight: '700',
    color: '#111827',
  },
  subhead: {
    fontSize: 14,
    color: '#6B7280',
    marginTop: 2,
  },
  bellButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: '#E5E7EB',
  },
  bellIcon: {
    fontSize: 18,
  },
  bellBadge: {
    position: 'absolute',
    top: -2,
    right: -2,
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: '#EF4444',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 3,
    borderWidth: 2,
    borderColor: '#F9FAFB',
  },
  bellBadgeText: {
    fontSize: 10,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  heroCard: {
    backgroundColor: '#1A1A1A',
    borderRadius: 18,
    padding: 24,
    marginBottom: 24,
  },
  heroLabel: {
    fontSize: 13,
    color: 'rgba(255,255,255,0.6)',
    fontWeight: '500',
    letterSpacing: 0.4,
    textTransform: 'uppercase',
    marginBottom: 8,
  },
  heroBalanceRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
  },
  heroCurrency: {
    fontSize: 18,
    color: 'rgba(255,255,255,0.8)',
    fontWeight: '500',
  },
  heroBalance: {
    fontSize: 36,
    fontWeight: '800',
    color: '#FFFFFF',
  },
  heroBalanceUnavailable: {
    fontSize: 28,
    fontWeight: '700',
    color: 'rgba(255,255,255,0.35)',
  },
  heroSub: {
    fontSize: 12,
    color: 'rgba(255,255,255,0.5)',
    marginTop: 8,
  },
  tileRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 28,
  },
  tile: {
    flex: 1,
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#E5E7EB',
  },
  tileIcon: {
    fontSize: 20,
    marginBottom: 6,
    color: '#1A1A1A',
    fontWeight: '700',
  },
  tileLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#111827',
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 14,
  },
  sectionTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: '#111827',
  },
  seeAll: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1A1A1A',
  },
  seeAllCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#E5E7EB',
    borderStyle: 'dashed',
    marginBottom: 12,
  },
  seeAllCardText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1A1A1A',
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: 40,
    paddingHorizontal: 24,
  },
  emptyIllustration: {
    fontSize: 56,
    marginBottom: 16,
  },
  emptyHeading: {
    fontSize: 20,
    fontWeight: '700',
    color: '#111827',
    marginBottom: 8,
  },
  emptyBody: {
    fontSize: 14,
    color: '#6B7280',
    textAlign: 'center',
    lineHeight: 21,
    marginBottom: 28,
  },
  emptyButton: {
    backgroundColor: '#1A1A1A',
    borderRadius: 10,
    paddingHorizontal: 28,
    paddingVertical: 14,
  },
  emptyButtonText: {
    fontSize: 15,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  fab: {
    alignSelf: 'center',
    marginTop: 12,
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: '#1A1A1A',
    alignItems: 'center',
    justifyContent: 'center',
  },
  fabIcon: {
    fontSize: 26,
    color: '#FFFFFF',
    lineHeight: 30,
    marginTop: -2,
  },
  deleteAccountLink: {
    alignSelf: 'center',
    marginTop: 32,
  },
  deleteAccountLinkText: {
    fontSize: 12,
    color: '#9CA3AF',
  },
});
