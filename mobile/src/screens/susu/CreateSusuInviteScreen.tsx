import React, { useEffect } from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { shareJoinCode } from '../../api/susu';
import { useSusuDetail } from '../../hooks/useSusuDetail';
import type { RootStackParamList } from '../../navigation/RootNavigator';

export function CreateSusuInviteScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const route = useRoute<RouteProp<RootStackParamList, 'CreateSusuInvite'>>();
  const { groupId, joinCode, groupName, contributionCedis, frequency, targetMemberCount } =
    route.params;

  const { group, fetch } = useSusuDetail(groupId);

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
      <View style={styles.stepRow}>
        <Text style={styles.stepLabel}>Step 2 of 3 · Invite members</Text>
        <View style={styles.progressBg}>
          <View style={styles.progressFill} />
        </View>
      </View>

      <View style={styles.codeHero} testID="join-code-card">
        <Text style={styles.codeLabel}>Share this code</Text>
        <Text style={styles.code} testID="join-code-text">
          {joinCode}
        </Text>
        <Text style={styles.codeHint}>Send this code to your future members so they can join.</Text>
      </View>

      <TouchableOpacity style={styles.shareBtn} onPress={handleShare} testID="share-btn">
        <Text style={styles.shareBtnText}>Share code</Text>
      </TouchableOpacity>

      <View style={styles.memberProgress} testID="member-progress">
        <Text style={styles.memberProgressLabel}>Members joined</Text>
        <Text style={styles.memberProgressCount}>
          {memberCount} / {targetMemberCount}
        </Text>
        <View style={styles.progressBg}>
          <View style={[styles.memberFill, memberFillStyle]} />
        </View>
        {memberCount < targetMemberCount && (
          <Text style={styles.memberProgressHint}>
            Waiting for {targetMemberCount - memberCount} more member
            {targetMemberCount - memberCount !== 1 ? 's' : ''} to join.
          </Text>
        )}
      </View>

      <View style={styles.summary} testID="group-summary">
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
      </View>

      <View style={styles.ctaRow}>
        <TouchableOpacity
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
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F9FAFB', padding: 20 },
  stepRow: { marginBottom: 24 },
  stepLabel: {
    fontSize: 12,
    color: '#6B7280',
    fontWeight: '600',
    marginBottom: 6,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  progressBg: { height: 4, backgroundColor: '#E5E7EB', borderRadius: 2 },
  progressFill: { height: 4, backgroundColor: '#111827', borderRadius: 2, width: '66%' },
  codeHero: {
    backgroundColor: '#111827',
    borderRadius: 16,
    padding: 24,
    alignItems: 'center',
    marginBottom: 16,
  },
  codeLabel: {
    color: '#9CA3AF',
    fontSize: 12,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 1,
    marginBottom: 8,
  },
  code: { color: '#FFFFFF', fontSize: 36, fontWeight: '900', letterSpacing: 6, marginBottom: 8 },
  codeHint: { color: '#6B7280', fontSize: 13, textAlign: 'center' },
  shareBtn: {
    backgroundColor: '#111827',
    paddingVertical: 14,
    borderRadius: 12,
    alignItems: 'center',
    marginBottom: 24,
  },
  shareBtnText: { color: '#FFFFFF', fontWeight: '700' },
  memberProgress: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
  },
  memberProgressLabel: { fontSize: 12, color: '#6B7280', marginBottom: 4 },
  memberProgressCount: { fontSize: 24, fontWeight: '800', color: '#111827', marginBottom: 8 },
  memberFill: { height: 4, backgroundColor: '#111827', borderRadius: 2 },
  memberProgressHint: { color: '#9CA3AF', fontSize: 12, marginTop: 6 },
  summary: { backgroundColor: '#FFFFFF', borderRadius: 12, padding: 16, marginBottom: 24 },
  summaryTitle: { fontSize: 16, fontWeight: '700', color: '#111827', marginBottom: 4 },
  summaryMeta: { fontSize: 13, color: '#6B7280' },
  ctaRow: { marginTop: 'auto' },
  continueBtn: {
    backgroundColor: '#059669',
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  continueBtnSecondary: { backgroundColor: '#F3F4F6' },
  continueBtnText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
  continueBtnTextSecondary: { color: '#111827' },
});
