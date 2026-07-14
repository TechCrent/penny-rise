import React, { useEffect, useCallback, useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  RefreshControl,
  ActivityIndicator,
  StyleSheet,
  Alert,
} from 'react-native';
import { useRoute, RouteProp } from '@react-navigation/native';
import { LinearGradient } from 'expo-linear-gradient';
import { useSusuDetail } from '../../hooks/useSusuDetail';
import { susuApi } from '../../api/susu';
import { RotationRing } from '../../components/susu/RotationRing';
import { ContributionStatusPill } from '../../components/susu/ContributionStatusPill';
import { ContributeBottomSheet } from '../../components/susu/ContributeBottomSheet';
import { PrimaryButton } from '../../components/PrimaryButton';
import { AnimatedNumber, Icon, PressableScale } from '../../components/ui';
import { colors, radii, spacing } from '../../theme';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import type { ContributionStatus } from '../../types/susu';

type SusuDetailRoute = RouteProp<RootStackParamList, 'SusuDetail'>;

function formatPotCedis(pesewas: number): string {
  return (pesewas / 100).toLocaleString('en-GH', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function formatDate(iso: string | null) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-GH', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

export function SusuDetailScreen() {
  const route = useRoute<SusuDetailRoute>();
  const { groupId } = route.params;

  const { group, loading, refreshing, error, fetch, refresh } = useSusuDetail(groupId);
  const [activating, setActivating] = useState(false);
  const [sheetVisible, setSheetVisible] = useState(false);

  useEffect(() => {
    fetch();
  }, [fetch]);

  // ── Activate handler ────────────────────────────────────────────────
  const handleActivate = useCallback(async () => {
    if (!group) return;
    Alert.alert(
      'Activate group?',
      `This will start the rotation. All ${group.target_member_count} members will be notified.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Activate',
          style: 'default',
          onPress: async () => {
            setActivating(true);
            try {
              await susuApi.activateGroup(groupId, `activate-${groupId}`);
              await fetch();
            } catch (e) {
              console.error(e);
              Alert.alert('Activation failed', (e as Error)?.message ?? 'Please try again.');
            } finally {
              setActivating(false);
            }
          },
        },
      ],
    );
  }, [group, groupId, fetch]);

  if (loading && !group) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color={colors.gold.base} />
      </View>
    );
  }

  if (error || !group) {
    return (
      <View style={styles.center}>
        <Text style={styles.errorText}>{error ?? 'Group not found.'}</Text>
        <PrimaryButton title="Retry" onPress={() => fetch()} style={styles.retryBtn} />
      </View>
    );
  }

  const isPending = group.status === 'PENDING';
  const isActive = group.status === 'ACTIVE';
  const isCompleted = group.status === 'COMPLETED';
  const round = group.current_round;
  const callerMember = group.caller_membership;

  const canActivate =
    isPending &&
    group.is_caller_organiser &&
    group.members.filter(m => m.membership_status === 'ACTIVE').length >= group.target_member_count;

  // Simplified: find a PENDING contribution when round is COLLECTING.
  // In production, caller_user_id from auth context is used to find the caller's own contribution.
  const myContrib =
    round?.contributions.find(c => c.status === 'PENDING' && round.status === 'COLLECTING') ?? null;
  const canContribute =
    isActive &&
    round?.status === 'COLLECTING' &&
    callerMember.rotation_position !== null &&
    myContrib !== null;

  const isDisbursing = round?.status === 'DISBURSING' || round?.status === 'DISBURSED';

  return (
    <View style={styles.container}>
      <ScrollView
        style={styles.screen}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={refresh}
            tintColor={colors.gold.base}
            colors={[colors.gold.base]}
          />
        }
        testID="susu-detail-screen"
      >
        {/* ── Status banner ─────────────────────────────────────────────── */}
        {isPending && (
          <View style={styles.pendingBanner}>
            <Text style={styles.pendingBannerText}>
              {group.members.filter(m => m.membership_status === 'ACTIVE').length} /
              {group.target_member_count} members joined · Waiting to start
            </Text>
            {group.is_caller_organiser && (
              <Text style={styles.joinCode}>Join code: {group.join_code}</Text>
            )}
          </View>
        )}

        {/* ── Current round hero ────────────────────────────────────────── */}
        {isActive && round && (
          <LinearGradient
            colors={[colors.heroFrom, colors.heroTo]}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 1 }}
            style={styles.roundHero}
            testID="round-hero"
          >
            <Text style={styles.roundLabel}>
              Round {round.round_number} of {round.total_rounds}
            </Text>
            <Text style={styles.recipientName}>{round.recipient_display_name}</Text>
            <Text style={styles.potAmount}>
              GHS <AnimatedNumber value={round.expected_pot_amount} formatter={formatPotCedis} />{' '}
              pot
            </Text>
            {round.scheduled_collection_at && (
              <Text style={styles.dueDate}>Due {formatDate(round.scheduled_collection_at)}</Text>
            )}

            {isDisbursing && (
              <View style={styles.disbursingBadge} testID="disbursing-badge">
                <Icon
                  name={round.status === 'DISBURSED' ? 'checkmark-circle' : 'hourglass-outline'}
                  size={13}
                  color={colors.status.success}
                />
                <Text style={styles.disbursingText}>
                  {round.status === 'DISBURSED'
                    ? 'Pot disbursed'
                    : 'Disbursing to ' + round.recipient_display_name}
                </Text>
              </View>
            )}
          </LinearGradient>
        )}

        {/* ── Completed state ────────────────────────────────────────────── */}
        {isCompleted && (
          <View style={[styles.roundHero, styles.completedHero]} testID="completed-hero">
            <Text style={styles.recipientName}>🎉 Susu Complete</Text>
            <Text style={styles.potAmount}>All rounds have completed</Text>
          </View>
        )}

        {/* ── Rotation ring ─────────────────────────────────────────────── */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Rotation</Text>
          <RotationRing
            members={group.members}
            currentRecipientUserId={round?.recipient_user_id ?? null}
            currentRoundNumber={group.current_round?.round_number ?? null}
            isPending={isPending}
          />
          {isPending && (
            <Text style={styles.pendingRingNote}>
              Rotation order will be set when the group activates.
            </Text>
          )}
        </View>

        {/* ── Contributions for current round ───────────────────────────── */}
        {round && round.contributions.length > 0 && (
          <View style={styles.section} testID="contributions-list">
            <Text style={styles.sectionTitle}>Round {round.round_number} Contributions</Text>
            {round.contributions.map(contrib => (
              <ContributionRow key={contrib.member_user_id} contrib={contrib} />
            ))}
          </View>
        )}

        {/* ── Members ───────────────────────────────────────────────────── */}
        <View style={styles.section} testID="members-list">
          <Text style={styles.sectionTitle}>Members ({group.members.length})</Text>
          {group.members.map(m => (
            <View key={m.user_id} style={styles.memberRow}>
              <View style={styles.memberLeft}>
                <View style={styles.positionBadge}>
                  <Text style={styles.positionText}>{m.rotation_position ?? '?'}</Text>
                </View>
                <View>
                  <Text style={styles.memberName}>
                    {m.display_name}
                    {m.is_organiser && <Text style={styles.organiserLabel}> (organiser)</Text>}
                  </Text>
                </View>
              </View>
            </View>
          ))}
        </View>

        {/* ── Primary CTA ───────────────────────────────────────────────── */}
        <View style={styles.ctaContainer}>
          {canActivate && (
            <PressableScale
              style={[styles.ctaBtn, styles.ctaBtnActivate, activating && styles.ctaBtnDisabled]}
              onPress={handleActivate}
              disabled={activating}
              testID="activate-btn"
            >
              <Text style={styles.ctaBtnText}>{activating ? 'Activating…' : 'Activate Group'}</Text>
            </PressableScale>
          )}

          {isPending && !group.is_caller_organiser && (
            <View style={styles.pendingMemberNote}>
              <Text style={styles.pendingMemberNoteText}>
                Waiting for the organiser to activate the group.
              </Text>
            </View>
          )}

          {canContribute && (
            <PressableScale
              style={styles.ctaBtn}
              onPress={() => setSheetVisible(true)}
              testID="contribute-cta"
            >
              <Text style={styles.ctaBtnText}>
                {'Pay GHS '}
                {group.contribution_amount_cedis}
                {' now'}
              </Text>
            </PressableScale>
          )}
        </View>

        <View style={styles.spacer} />
      </ScrollView>
      {group && (
        <ContributeBottomSheet
          visible={sheetVisible}
          group={group}
          onClose={() => setSheetVisible(false)}
          onSuccess={() => {
            setSheetVisible(false);
            refresh();
          }}
        />
      )}
    </View>
  );
}

function ContributionRow({ contrib }: { contrib: ContributionStatus }) {
  return (
    <View style={styles.contribRow} testID={`contrib-row-${contrib.member_user_id}`}>
      <Text style={styles.contribName} numberOfLines={1}>
        {contrib.display_name}
      </Text>
      <ContributionStatusPill status={contrib.status} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  screen: { flex: 1, backgroundColor: colors.background },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl },
  errorText: {
    color: colors.status.error,
    fontSize: 14,
    textAlign: 'center',
    marginBottom: spacing.md,
  },
  retryBtn: { paddingHorizontal: spacing.xl },

  pendingBanner: { backgroundColor: colors.status.warningBg, padding: spacing.lg },
  pendingBannerText: { color: colors.status.warningText, fontSize: 13, fontWeight: '500' },
  joinCode: {
    color: colors.status.warningText,
    fontSize: 15,
    fontWeight: '800',
    marginTop: spacing.xs,
  },

  roundHero: { padding: spacing.xl },
  completedHero: { backgroundColor: colors.status.successText },
  roundLabel: {
    color: colors.textOnDarkMuted,
    fontSize: 12,
    fontWeight: '600',
    marginBottom: spacing.xs,
  },
  recipientName: {
    color: colors.textOnDark,
    fontSize: 22,
    fontWeight: '800',
    marginBottom: spacing.xs,
  },
  potAmount: { color: '#D1FAE5', fontSize: 16, fontWeight: '600', marginBottom: 2 },
  dueDate: { color: colors.textOnDarkMuted, fontSize: 13, marginTop: 2 },
  disbursingBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
    backgroundColor: 'rgba(255,255,255,0.1)',
    borderRadius: radii.sm,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.xs,
    alignSelf: 'flex-start',
    marginTop: spacing.md,
  },
  disbursingText: { color: '#D1FAE5', fontSize: 13, fontWeight: '600' },

  section: { backgroundColor: colors.surface, marginTop: spacing.sm, padding: spacing.lg },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: colors.neutral[700],
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: spacing.md,
  },

  pendingRingNote: {
    textAlign: 'center',
    color: colors.textTertiary,
    fontSize: 12,
    marginTop: spacing.md,
  },

  contribRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: spacing.sm,
    borderBottomWidth: 1,
    borderBottomColor: colors.neutral[100],
  },
  contribName: { fontSize: 14, color: colors.textPrimary, flex: 1, marginRight: spacing.sm },

  memberRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: spacing.sm,
    borderBottomWidth: 1,
    borderBottomColor: colors.neutral[100],
  },
  memberLeft: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  positionBadge: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: colors.neutral[100],
    alignItems: 'center',
    justifyContent: 'center',
  },
  positionText: { fontSize: 12, fontWeight: '700', color: colors.neutral[700] },
  memberName: { fontSize: 14, color: colors.textPrimary, fontWeight: '500' },
  organiserLabel: { color: colors.textSecondary, fontWeight: '400' },

  ctaContainer: { padding: spacing.lg },
  ctaBtn: {
    backgroundColor: colors.gold.base,
    paddingVertical: spacing.lg,
    borderRadius: radii.md,
    alignItems: 'center',
  },
  ctaBtnActivate: { backgroundColor: colors.status.success },
  ctaBtnDisabled: { opacity: 0.5 },
  ctaBtnText: { color: colors.neutral[900], fontSize: 16, fontWeight: '700' },
  pendingMemberNote: {
    padding: spacing.lg,
    backgroundColor: colors.neutral[100],
    borderRadius: radii.md,
    alignItems: 'center',
  },
  pendingMemberNoteText: { color: colors.textSecondary, fontSize: 14, textAlign: 'center' },
  spacer: { height: 40 },
});
