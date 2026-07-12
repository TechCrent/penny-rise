import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ScrollView, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { useAuth } from '../../auth/AuthContext';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Settings'>;

interface SettingsRowConfig {
  label: string;
  onPress: () => void;
  destructive?: boolean;
}

function SettingsRow({ label, onPress, destructive }: SettingsRowConfig) {
  return (
    <TouchableOpacity style={styles.row} onPress={onPress} activeOpacity={0.7}>
      <Text style={[styles.rowLabel, destructive ? styles.rowLabelDestructive : null]}>
        {label}
      </Text>
      <Text style={styles.chevron}>›</Text>
    </TouchableOpacity>
  );
}

export function SettingsScreen() {
  const navigation = useNavigation<Nav>();
  const { clearTokens } = useAuth();

  const confirmLogout = () => {
    Alert.alert('Log out?', 'You will need to sign in again to access your account.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Log out', style: 'destructive', onPress: () => clearTokens() },
    ]);
  };

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.header}>
          <TouchableOpacity onPress={() => navigation.goBack()} hitSlop={8}>
            <Text style={styles.backText}>← Back</Text>
          </TouchableOpacity>
        </View>
        <Text style={styles.heading}>Settings</Text>

        <Text style={styles.sectionLabel}>Account</Text>
        <View style={styles.section}>
          <SettingsRow label="Profile" onPress={() => navigation.navigate('EditProfile')} />
          <SettingsRow
            label="Change password"
            onPress={() => navigation.navigate('ChangePassword')}
          />
          <SettingsRow label="App Lock" onPress={() => navigation.navigate('AppLockSettings')} />
        </View>

        <Text style={styles.sectionLabel}>Legal</Text>
        <View style={styles.section}>
          <SettingsRow label="Terms & Privacy" onPress={() => navigation.navigate('Legal')} />
        </View>

        <Text style={styles.sectionLabel}>Account management</Text>
        <View style={styles.section}>
          <SettingsRow
            label="Delete account"
            onPress={() => navigation.navigate('DeleteAccount')}
          />
        </View>

        <View style={styles.section}>
          <SettingsRow label="Log out" onPress={confirmLogout} destructive />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F9FAFB' },
  content: { paddingHorizontal: 20, paddingBottom: 40 },
  header: { marginTop: 8, marginBottom: 16 },
  backText: { color: '#1A1A1A', fontSize: 15 },
  heading: { fontSize: 24, fontWeight: '700', color: '#111827', marginBottom: 24 },
  sectionLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#9CA3AF',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: 8,
    marginTop: 20,
  },
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
  rowLabel: { fontSize: 15, color: '#111827', fontWeight: '500' },
  rowLabelDestructive: { color: '#EF4444' },
  chevron: { fontSize: 18, color: '#9CA3AF' },
});
