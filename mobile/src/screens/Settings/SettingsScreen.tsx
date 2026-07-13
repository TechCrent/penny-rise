import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ScrollView, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { useAuth } from '../../auth/AuthContext';
import { PressableScale } from '../../components/ui';
import { colors, radii, spacing, typography } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Settings'>;

interface SettingsRowConfig {
  label: string;
  onPress: () => void;
  destructive?: boolean;
}

function SettingsRow({ label, onPress, destructive }: SettingsRowConfig) {
  return (
    <PressableScale style={styles.row} onPress={onPress}>
      <Text style={[styles.rowLabel, destructive ? styles.rowLabelDestructive : null]}>
        {label}
      </Text>
      <Text style={styles.chevron}>›</Text>
    </PressableScale>
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
  safe: { flex: 1, backgroundColor: colors.background },
  content: { paddingHorizontal: spacing.xl, paddingBottom: spacing['4xl'] },
  header: { marginTop: spacing.sm, marginBottom: spacing.lg },
  backText: { color: colors.textPrimary, fontSize: 15 },
  heading: { ...typography.h1, color: colors.textPrimary, marginBottom: spacing['2xl'] },
  sectionLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: colors.textTertiary,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: spacing.sm,
    marginTop: spacing.xl,
  },
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
  rowLabel: { ...typography.bodyMedium, color: colors.textPrimary },
  rowLabelDestructive: { color: colors.status.error },
  chevron: { fontSize: 18, color: colors.textTertiary },
});
