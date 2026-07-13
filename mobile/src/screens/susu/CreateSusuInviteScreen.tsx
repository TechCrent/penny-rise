import React, { useEffect } from 'react';
import { View, Text, ActivityIndicator, StyleSheet } from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { LinearGradient } from 'expo-linear-gradient';
import Animated from 'react-native-reanimated';
import { shareJoinCode } from '../../api/susu';
import { useSusuDetail } from '../../hooks/useSusuDetail';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { Icon, PressableScale, ScreenHeader, fadeInUp } from '../../components/ui';
import { colors, radii, shadows, spacing, typography } from '../../theme';

export function CreateSusuInviteScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const route = useRoute<RouteProp<RootStackParamList, 'CreateSusuInvite'>>();
  const { groupId, joinCode, groupName, contributionCedis, frequency, targetMemberCount } =
    route.params;

  const { group, loading, error, fetch } = useSusuDetail(groupId);

  useEffect(() => {
    fetch();
  }, [fetch]);

  const memberCount = group
    ? group.members.filter(m => m.membership_status === 'ACTIVE').length
    : 1;

  const handleShare = async () => {
    await shareJoinCode(joinCode, groupName);
  };

  const handleContinue = () => {
    navigation.navigate('SusuDetail', { groupId });
  };

  const fillPct = Math.min(Math.round((memberCount / targetMemberCount) * 100), 100);
  const memberFillStyle = { width: `${fillPct}%` as `${number}%` };

  return (
    <View style={styles.screen} testID="create-susu-invite-screen">
      <ScreenHeader onBack={() => navigation.goBack()} />

      <Animated.View entering={fadeInUp(40)} style={styles.stepRow}>
        <Text style={styles.stepLabel}>Step 2 of 3 · Invite members</Text>
        <View style={styles.progressBg}>
          <View style={styles.progressFill} />
        </View>
      </Animated.View>

      <Animated.View entering={fadeInUp(80)}>
        <LinearGradient
          colors={[colors.heroFrom, colors.heroTo]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={styles.codeHero}
          testID="join-code-card"
        >
          <Text style={styles.codeLabel}>Share this code</Text>
          <Text style={styles.code} testID="join-code-text">
            {joinCode}
          </Text>
          <Text style={styles.codeHint}>Send this code to your future members so they can join.</Text>
        </LinearGradient>

        <PressableScale style={styles.shareBtn} onPress={handleShare} testID="share-btn">
          <Icon name="share-social-outline" size={18} color={colors.neutral[900]} />
          <Text style={styles.shareBtnText}>Share code</Text>
        </PressableScale>
      </Animated.View>

      <Animated.View entering={fadeInUp(120)} style={styles.memberProgress} testID="member-progress">
        <Text style={styles.memberProgressLabel}>Members joined</Text>
        <Text style={styles.memberProgressCount}>
          {memberCount} / {targetMemberCount}
        </Text>
        <View style={styles.progressBg}>
          <View style={[styles.memberFill, memberFillStyle]} />
        </View>
        {memberCount < targetMemberCount && !error && (
          <Text style={styles.memberProgressHint}>
            Waiting for {targetMemberCount - memberCount} more member
            {targetMemberCount - memberCount !== 1 ? 's' : ''} to join.
          </Text>
        )}
        {loading && (
          <View style={styles.memberProgressLoading} testID="member-progress-loading">
            <ActivityIndicator size="small" color={colors.gold.base} />
          </View>
        )}
        {error && (
          <View style={styles.memberProgressError} testID="member-progress-error">
            <Text style={styles.memberProgressErrorText}>
              Couldn&apos;t refresh member count. It may be out of date.
            </Text>
            <PressableScale onPress={() => fetch()} testID="member-progress-retry">
              <Text style={styles.memberProgressRetryText}>Retry</Text>
            </PressableScale>
          </View>
        )}
      </Animated.View>

      <Animated.View entering={fadeInUp(160)} style={styles.summary} testID="group-summary">
        <Text style={styles.summaryTitle}>{groupName}</Text>
        <Text style={styles.summaryMeta}>
          {'GHS '}
          {contributionCedis}
          {' · '}
          {frequency === 'BIWEEKLY'
            ? 'Every 2 weeks'
            : frequency === 'WEEKLY'
              ? 'Weekly'
              : 'Monthly'}
          {' · '}
          {targetMemberCount}
          {' members'}
        </Text>
      </Animated.View>

      <View style={styles.ctaRow}>
        <PressableScale
          style={[
            styles.continueBtn,
            memberCount < targetMemberCount && styles.continueBtnSecondary,
          ]}
          onPress={handleContinue}
          testID="continue-btn"
        >
          <Text
            style={[
              styles.continueBtnText,
              memberCount < targetMemberCount && styles.continueBtnTextSecondary,
            ]}
          >
            {memberCount >= targetMemberCount ? 'Activate group →' : 'View group (invite more)'}
          </Text>
        </PressableScale>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background, padding: spacing.xl },
  stepRow: { marginBottom: spacing.xl },
  stepLabel: {
    fontSize: 12,
    color: colors.textSecondary,
    fontWeight: '600',
    marginBottom: spacing.xs,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  progressBg: { height: 6, backgroundColor: colors.neutral[200], borderRadius: radii.pill },
  progressFill: { height: 6, backgroundColor: colors.gold.base, borderRadius: radii.pill, width: '66%' },
  codeHero: {
    borderRadius: radii['2xl'],
    padding: spacing['2xl'],
    alignItems: 'center',
    marginBottom: spacing.md,
    ...shadows.lg,
  },
  codeLabel: {
    color: colors.textOnDarkMuted,
    fontSize: 12,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 1,
    marginBottom: spacing.md,
  },
  code: {
    ...typography.numericHero,
    color: colors.textOnDark,
    letterSpacing: 6,
    marginBottom: spacing.md,
  },
  codeHint: { color: colors.textOnDarkMuted, fontSize: 13, textAlign: 'center' },
  shareBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.sm,
    backgroundColor: colors.gold.base,
    paddingVertical: spacing.md,
    borderRadius: radii.md,
    marginBottom: spacing['2xl'],
    ...shadows.sm,
  },
  shareBtnText: { ...typography.button, fontSize: 15, color: colors.neutral[900] },
  memberProgress: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
    marginBottom: spacing.lg,
    ...shadows.sm,
  },
  memberProgressLabel: { ...typography.label, color: colors.textSecondary, marginBottom: spacing.xs },
  memberProgressCount: { ...typography.numericLarge, color: colors.textPrimary, marginBottom: spacing.sm },
  memberFill: { height: 6, backgroundColor: colors.gold.base, borderRadius: radii.pill },
  memberProgressHint: { color: colors.textTertiary, fontSize: 12, marginTop: spacing.sm },
  memberProgressLoading: { marginTop: spacing.sm, alignItems: 'flex-start' },
  memberProgressError: { marginTop: spacing.sm },
  memberProgressErrorText: { color: colors.status.error, fontSize: 12 },
  memberProgressRetryText: { color: colors.textPrimary, fontSize: 12, fontWeight: '700', marginTop: spacing.xs },
  summary: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
    marginBottom: spacing['2xl'],
    ...shadows.sm,
  },
  summaryTitle: { ...typography.h3, color: colors.textPrimary, marginBottom: spacing.xs },
  summaryMeta: { fontSize: 13, color: colors.textSecondary },
  ctaRow: { marginTop: 'auto' },
  continueBtn: {
    backgroundColor: colors.status.success,
    paddingVertical: spacing.lg,
    borderRadius: radii.md,
    alignItems: 'center',
    ...shadows.sm,
  },
  continueBtnSecondary: { backgroundColor: colors.neutral[100], ...shadows.none },
  continueBtnText: { ...typography.button, color: colors.neutral[900] },
  continueBtnTextSecondary: { color: colors.textPrimary },
});
