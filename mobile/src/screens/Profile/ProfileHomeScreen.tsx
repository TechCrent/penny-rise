import React from 'react';
import { View, Text, TouchableOpacity, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { CompositeNavigationProp } from '@react-navigation/native';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import type { MainTabParamList } from '../../navigation/MainTabNavigator';
import { useProfile } from '../Settings/useProfile';

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
              <Text style={[styles.pillText, styles.pillTextVerified]}>Verified</Text>
            </View>
          </View>
          <Text style={[styles.identityBody, styles.identityBodyGreen]}>
            Your identity has been verified.
          </Text>
        </View>

        <View style={styles.section}>
          <TouchableOpacity
            style={[styles.row, styles.rowLast]}
            onPress={() => navigation.navigate('Settings')}
            activeOpacity={0.7}
          >
            <Text style={styles.rowLabel}>Settings &amp; security</Text>
            <Text style={styles.chevron}>›</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F9FAFB' },
  content: { paddingHorizontal: 20, paddingTop: 24, paddingBottom: 40 },
  header: { alignItems: 'center', marginBottom: 24 },
  avatar: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: '#4F46E5',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 12,
  },
  avatarText: { fontSize: 26, fontWeight: '700', color: '#FFFFFF' },
  name: { fontSize: 19, fontWeight: '700', color: '#111827' },
  email: { fontSize: 13, color: '#6B7280', marginTop: 2 },

  identityCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 18,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#E5E7EB',
  },
  identityHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  identityTitle: { fontSize: 15, fontWeight: '700', color: '#111827' },
  pill: {
    backgroundColor: '#F3F4F6',
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  pillVerified: { backgroundColor: '#DCFCE7' },
  pillText: { fontSize: 11, fontWeight: '700', color: '#6B7280' },
  pillTextVerified: { color: '#16A34A' },
  identityBody: { fontSize: 13, color: '#6B7280', lineHeight: 19, marginBottom: 12 },
  identityBodyGreen: { color: '#166534', marginBottom: 0 },

  section: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#E5E7EB',
    overflow: 'hidden',
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
  },
  rowLast: { borderBottomWidth: 0 },
  rowLabel: { fontSize: 15, color: '#111827', fontWeight: '500' },
  chevron: { fontSize: 18, color: '#9CA3AF' },
});
