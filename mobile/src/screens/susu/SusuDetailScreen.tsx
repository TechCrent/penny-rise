import React, { useEffect, useCallback, useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  RefreshControl,
  ActivityIndicator,
  StyleSheet,
  Alert,
} from 'react-native';
import { useRoute, RouteProp } from '@react-navigation/native';
import { useSusuDetail } from '../../hooks/useSusuDetail';
import { susuApi } from '../../api/susu';
import { RotationRing } from '../../components/susu/RotationRing';
import { ContributionStatusPill } from '../../components/susu/ContributionStatusPill';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import type { ContributionStatus } from '../../types/susu';

type SusuDetailRoute = RouteProp<RootStackParamList, 'SusuDetail'>;

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
  const [contributing, setContributing] = useState(false);

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
              Alert.alert('Activation failed', (e as Error)?.message ?? 'Please try again.');
            } finally {
              setActivating(false);
            }
          },
        },
      ],
    );
  }, [group, groupId, fetch]);

  // ── Contribute handler ───────────────────────────────────────────────
  const handleContribute = useCallback(async () => {
    if (!group?.current_round) return;
    const roundId = group.current_round.id;
    const amount = group.contribution_amount_cedis;

    Alert.alert(
      `Pay GHS ${amount}?`,
      'This will transfer your contribution from your wallet to the susu pot.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: `Pay GHS ${amount}`,
          style: 'default',
          onPress: async () => {
            setContributing(true);
            try {
              await susuApi.payContribution(roundId, `contrib-${roundId}-${Date.now()}`);
              await fetch();
            } catch (e) {
              Alert.alert('Payment failed', (e as Error)?.message ?? 'Please try again.');
            } finally {
              setContributing(false);
            }
          },
        },
      ],
    );
  }, [group, fetch]);

  if (loading && !group) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#111827" />
      </View>
    );
  }

  if (error || !group) {
    return (
      <View style={styles.center}>
        <Text style={styles.errorText}>{error ?? 'Group not found.'}</Text>
        <TouchableOpacity onPress={() => fetch()} style={styles.retryBtn}>
          <Text style={styles.retryText}>Retry</Text>
        </TouchableOpacity>
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
    <ScrollView
      style={styles.screen}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
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
        <View style={styles.roundHero} testID="round-hero">
          <Text style={styles.roundLabel}>
            Round {round.round_number} of {round.total_rounds}
          </Text>
          <Text style={styles.recipientName}>{round.recipient_display_name}</Text>
          <Text style={styles.potAmount}>GHS {round.expected_pot_amount_cedis} pot</Text>
          {round.scheduled_collection_at && (
            <Text style={styles.dueDate}>Due {formatDate(round.scheduled_collection_at)}</Text>
          )}

          {isDisbursing && (
            <View style={styles.disbursingBadge} testID="disbursing-badge">
              <Text style={styles.disbursingText}>
                {round.status === 'DISBURSED'
                  ? '✓ Pot disbursed'
                  : '⏳ Disbursing to ' + round.recipient_display_name}
              </Text>
            </View>
          )}
        </View>
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
          <TouchableOpacity
            style={[styles.ctaBtn, styles.ctaBtnActivate, activating && styles.ctaBtnDisabled]}
            onPress={handleActivate}
            disabled={activating}
            testID="activate-btn"
          >
            <Text style={styles.ctaBtnText}>{activating ? 'Activating…' : 'Activate Group'}</Text>
          </TouchableOpacity>
        )}

        {isPending && !group.is_caller_organiser && (
          <View style={styles.pendingMemberNote}>
            <Text style={styles.pendingMemberNoteText}>
              Waiting for the organiser to activate the group.
            </Text>
          </View>
        )}

        {canContribute && (
          <TouchableOpacity
            style={[styles.ctaBtn, contributing && styles.ctaBtnDisabled]}
            onPress={handleContribute}
            disabled={contributing}
            testID="contribute-btn"
          >
            <Text style={styles.ctaBtnText}>
              {contributing ? 'Processing…' : `Pay my GHS ${group.contribution_amount_cedis}`}
            </Text>
          </TouchableOpacity>
        )}
      </View>

      <View style={styles.spacer} />
    </ScrollView>
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
  screen: { flex: 1, backgroundColor: '#F9FAFB' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  errorText: { color: '#EF4444', fontSize: 14, textAlign: 'center', marginBottom: 16 },
  retryBtn: {
    paddingHorizontal: 24,
    paddingVertical: 12,
    backgroundColor: '#111827',
    borderRadius: 8,
  },
  retryText: { color: '#FFFFFF', fontWeight: '700' },

  pendingBanner: { backgroundColor: '#FEF9C3', padding: 16, marginBottom: 0 },
  pendingBannerText: { color: '#713F12', fontSize: 13, fontWeight: '500' },
  joinCode: { color: '#713F12', fontSize: 15, fontWeight: '800', marginTop: 4 },

  roundHero: { backgroundColor: '#111827', padding: 20, marginBottom: 0 },
  completedHero: { backgroundColor: '#065F46' },
  roundLabel: { color: '#9CA3AF', fontSize: 12, fontWeight: '600', marginBottom: 4 },
  recipientName: { color: '#FFFFFF', fontSize: 22, fontWeight: '800', marginBottom: 4 },
  potAmount: { color: '#D1FAE5', fontSize: 16, fontWeight: '600', marginBottom: 2 },
  dueDate: { color: '#9CA3AF', fontSize: 13, marginTop: 2 },
  disbursingBadge: {
    backgroundColor: '#1F2937',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 6,
    alignSelf: 'flex-start',
    marginTop: 12,
  },
  disbursingText: { color: '#D1FAE5', fontSize: 13, fontWeight: '600' },

  section: { backgroundColor: '#FFFFFF', marginTop: 8, padding: 16 },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: '#374151',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 12,
  },

  pendingRingNote: { textAlign: 'center', color: '#9CA3AF', fontSize: 12, marginTop: 12 },

  contribRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
  },
  contribName: { fontSize: 14, color: '#111827', flex: 1, marginRight: 8 },

  memberRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
  },
  memberLeft: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  positionBadge: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: '#F3F4F6',
    alignItems: 'center',
    justifyContent: 'center',
  },
  positionText: { fontSize: 12, fontWeight: '700', color: '#374151' },
  memberName: { fontSize: 14, color: '#111827', fontWeight: '500' },
  organiserLabel: { color: '#6B7280', fontWeight: '400' },

  ctaContainer: { padding: 16 },
  ctaBtn: {
    backgroundColor: '#111827',
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  ctaBtnActivate: { backgroundColor: '#059669' },
  ctaBtnDisabled: { opacity: 0.5 },
  ctaBtnText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
  pendingMemberNote: {
    padding: 16,
    backgroundColor: '#F3F4F6',
    borderRadius: 12,
    alignItems: 'center',
  },
  pendingMemberNoteText: { color: '#6B7280', fontSize: 14, textAlign: 'center' },
  spacer: { height: 40 },
});
