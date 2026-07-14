import React from 'react';
import { View, Text, StyleSheet, ScrollView, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import Animated from 'react-native-reanimated';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { useAuth } from '../../auth/AuthContext';
import { PressableScale, ScreenHeader, Icon, fadeInUp, type IconName } from '../../components/ui';
import { colors, radii, shadows, spacing, typography } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Settings'>;

interface SettingsRowConfig {
  label: string;
  icon: IconName;
  onPress: () => void;
  destructive?: boolean;
  isLast?: boolean;
}

function SettingsRow({ label, icon, onPress, destructive, isLast }: SettingsRowConfig) {
  return (
    <PressableScale style={[styles.row, isLast ? styles.rowLast : null]} onPress={onPress}>
      <View style={[styles.rowBadge, destructive ? styles.rowBadgeDestructive : null]}>
        <Icon name={icon} size={18} color={destructive ? colors.status.error : colors.gold.text} />
      </View>
      <Text style={[styles.rowLabel, destructive ? styles.rowLabelDestructive : null]}>
        {label}
      </Text>
      <Icon name="arrow-forward" size={18} color={colors.textTertiary} />
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
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <ScreenHeader title="Settings" onBack={() => navigation.goBack()} />

        <Animated.View entering={fadeInUp(60)}>
          <Text style={styles.sectionLabel}>Account</Text>
          <View style={styles.section}>
            <SettingsRow
              label="Profile"
              icon="person-circle-outline"
              onPress={() => navigation.navigate('EditProfile')}
            />
            <SettingsRow
              label="Change password"
              icon="lock-closed-outline"
              onPress={() => navigation.navigate('ChangePassword')}
            />
            <SettingsRow
              label="App Lock"
              icon="shield-checkmark-outline"
              onPress={() => navigation.navigate('AppLockSettings')}
              isLast
            />
          </View>
        </Animated.View>

        <Animated.View entering={fadeInUp(120)}>
          <Text style={styles.sectionLabel}>Legal</Text>
          <View style={styles.section}>
            <SettingsRow
              label="Terms & Privacy"
              icon="receipt-outline"
              onPress={() => navigation.navigate('Legal')}
              isLast
            />
          </View>
        </Animated.View>

        <Animated.View entering={fadeInUp(180)}>
          <Text style={styles.sectionLabel}>Account management</Text>
          <View style={styles.section}>
            <SettingsRow
              label="Delete account"
              icon="warning-outline"
              onPress={() => navigation.navigate('DeleteAccount')}
              destructive
              isLast
            />
          </View>

          <View style={[styles.section, styles.sectionLoose]}>
            <SettingsRow
              label="Log out"
              icon="lock-open-outline"
              onPress={confirmLogout}
              destructive
              isLast
            />
          </View>
        </Animated.View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  content: { paddingHorizontal: spacing.xl, paddingTop: spacing.md, paddingBottom: spacing['4xl'] },
  sectionLabel: {
    ...typography.label,
    color: colors.textTertiary,
    marginBottom: spacing.sm,
    marginTop: spacing.xl,
  },
  section: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
    ...shadows.sm,
  },
  sectionLoose: { marginTop: spacing.lg },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    minHeight: 56,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.neutral[100],
  },
  rowLast: { borderBottomWidth: 0 },
  rowBadge: {
    width: 38,
    height: 38,
    borderRadius: radii.md,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
  },
  rowBadgeDestructive: { backgroundColor: colors.status.errorBg },
  rowLabel: { ...typography.bodyMedium, color: colors.textPrimary, flex: 1 },
  rowLabelDestructive: { color: colors.status.error },
});
