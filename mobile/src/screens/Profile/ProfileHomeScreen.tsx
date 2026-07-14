import React from 'react';
import { View, Text, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { CompositeNavigationProp } from '@react-navigation/native';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import Animated from 'react-native-reanimated';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import type { MainTabParamList } from '../../navigation/MainTabNavigator';
import { useProfile } from '../Settings/useProfile';
import { GradientHero, Icon, PressableScale, fadeInUp } from '../../components/ui';
import { colors, radii, shadows, spacing, typography } from '../../theme';

type Nav = CompositeNavigationProp<
  BottomTabNavigationProp<MainTabParamList, 'Profile'>,
  NativeStackNavigationProp<RootStackParamList>
>;

export function ProfileHomeScreen() {
  const navigation = useNavigation<Nav>();
  const { profileQuery } = useProfile();
  const profile = profileQuery.data;

  return (
    <SafeAreaView style={styles.safe} testID="profile-home-screen">
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <Animated.View entering={fadeInUp(40)}>
          <GradientHero
            icon="person-circle-outline"
            title={profile?.display_name ?? '—'}
            subtitle={profile?.email ?? ''}
          />
        </Animated.View>

        <Animated.View entering={fadeInUp(110)} style={styles.identityCard}>
          <View style={styles.identityHeader}>
            <Text style={styles.identityTitle}>Identity verification</Text>
            <View style={[styles.pill, styles.pillVerified]}>
              <Icon name="checkmark-circle" size={12} color={colors.status.successText} />
              <Text style={[styles.pillText, styles.pillTextVerified]}>Verified</Text>
            </View>
          </View>
          <Text style={[styles.identityBody, styles.identityBodyGreen]}>
            Your identity has been verified.
          </Text>
        </Animated.View>

        <Animated.View entering={fadeInUp(180)}>
          <View style={styles.section}>
            <PressableScale
              style={[styles.row, styles.rowLast]}
              onPress={() => navigation.navigate('Settings')}
            >
              <View style={styles.rowBadge}>
                <Icon name="shield-checkmark-outline" size={18} color={colors.gold.text} />
              </View>
              <Text style={styles.rowLabel}>Settings &amp; security</Text>
              <Icon name="arrow-forward" size={18} color={colors.textTertiary} />
            </PressableScale>
          </View>
        </Animated.View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  content: {
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.md,
    paddingBottom: spacing['4xl'],
  },
  identityCard: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    padding: spacing.xl,
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
  identityBody: {
    fontSize: 13,
    color: colors.textSecondary,
    lineHeight: 19,
    marginBottom: spacing.md,
  },
  identityBodyGreen: { color: colors.status.successText, marginBottom: 0 },
  section: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
    ...shadows.sm,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.lg,
    borderBottomWidth: 1,
    borderBottomColor: colors.neutral[100],
  },
  rowLast: { borderBottomWidth: 0 },
  rowBadge: {
    width: 36,
    height: 36,
    borderRadius: radii.md,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
  },
  rowLabel: { ...typography.bodyMedium, color: colors.textPrimary, flex: 1 },
});
