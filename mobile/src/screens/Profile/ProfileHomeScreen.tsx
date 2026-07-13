import React from 'react';
import { View, Text, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { CompositeNavigationProp } from '@react-navigation/native';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Ionicons } from '@expo/vector-icons';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import type { MainTabParamList } from '../../navigation/MainTabNavigator';
import { useProfile } from '../Settings/useProfile';
import { PressableScale } from '../../components/ui';
import { colors, radii, shadows, spacing, typography } from '../../theme';

type Nav = CompositeNavigationProp<
  BottomTabNavigationProp<MainTabParamList, 'Profile'>,
  NativeStackNavigationProp<RootStackParamList>
>;

function initialsFor(name: string | undefined): string {
  if (!name) return '?';
  const parts = name.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? '';
  const last = parts.length > 1 ? parts[parts.length - 1][0] : '';
  return (first + last).toUpperCase();
}

export function ProfileHomeScreen() {
  const navigation = useNavigation<Nav>();
  const { profileQuery } = useProfile();
  const profile = profileQuery.data;

  return (
    <SafeAreaView style={styles.safe} testID="profile-home-screen">
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>{initialsFor(profile?.display_name)}</Text>
          </View>
          <Text style={styles.name}>{profile?.display_name ?? '—'}</Text>
          <Text style={styles.email}>{profile?.email ?? ''}</Text>
        </View>

        <View style={styles.identityCard}>
          <View style={styles.identityHeader}>
            <Text style={styles.identityTitle}>Identity verification</Text>
            <View style={[styles.pill, styles.pillVerified]}>
              <Ionicons name="checkmark-circle" size={12} color={colors.status.successText} />
              <Text style={[styles.pillText, styles.pillTextVerified]}>Verified</Text>
            </View>
          </View>
          <Text style={[styles.identityBody, styles.identityBodyGreen]}>
            Your identity has been verified.
          </Text>
        </View>

        <View style={styles.section}>
          <PressableScale
            style={[styles.row, styles.rowLast]}
            onPress={() => navigation.navigate('Settings')}
          >
            <Text style={styles.rowLabel}>Settings &amp; security</Text>
            <Text style={styles.chevron}>›</Text>
          </PressableScale>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  content: { paddingHorizontal: spacing.xl, paddingTop: spacing['2xl'], paddingBottom: spacing['4xl'] },
  header: { alignItems: 'center', marginBottom: spacing['2xl'] },
  avatar: {
    width: 72,
    height: 72,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.md,
  },
  avatarText: { fontSize: 26, fontWeight: '700', color: colors.gold.text },
  name: { fontSize: 19, fontWeight: '700', color: colors.textPrimary },
  email: { fontSize: 13, color: colors.textSecondary, marginTop: 2 },

  identityCard: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    padding: spacing.lg,
    marginBottom: spacing.xl,
    borderWidth: 1,
    borderColor: colors.border,
    ...shadows.sm,
  },
  identityHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.sm,
  },
  identityTitle: { fontSize: 15, fontWeight: '700', color: colors.textPrimary },
  pill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: colors.neutral[100],
    borderRadius: radii.pill,
    paddingHorizontal: spacing.sm,
    paddingVertical: 4,
  },
  pillVerified: { backgroundColor: colors.status.successBg },
  pillText: { fontSize: 11, fontWeight: '700', color: colors.textSecondary },
  pillTextVerified: { color: colors.status.successText },
  identityBody: { fontSize: 13, color: colors.textSecondary, lineHeight: 19, marginBottom: spacing.md },
  identityBodyGreen: { color: colors.status.successText, marginBottom: 0 },

  section: {
    backgroundColor: colors.surface,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.lg,
    borderBottomWidth: 1,
    borderBottomColor: colors.neutral[100],
  },
  rowLast: { borderBottomWidth: 0 },
  rowLabel: { ...typography.bodyMedium, color: colors.textPrimary },
  chevron: { fontSize: 18, color: colors.textTertiary },
});
