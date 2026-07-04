import React, { useState, useRef, useCallback, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  ScrollView,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { susuApi } from '../../api/susu';
import type { RootStackParamList } from '../../navigation/RootNavigator';

const CODE_LENGTH = 8;

function CodeInput({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const inputRef = useRef<TextInput>(null);

  const chars = value.toUpperCase().split('');
  while (chars.length < CODE_LENGTH) chars.push('');

  return (
    <TouchableOpacity
      activeOpacity={1}
      onPress={() => inputRef.current?.focus()}
      style={styles.codeRow}
      testID="code-input-row"
    >
      {chars.map((ch, idx) => (
        <View
          key={idx}
          style={[
            styles.codeBox,
            ch ? styles.codeBoxFilled : styles.codeBoxEmpty,
            idx === value.length && styles.codeBoxCursor,
          ]}
          testID={`code-box-${idx}`}
        >
          <Text style={styles.codeChar}>{ch}</Text>
        </View>
      ))}
      <TextInput
        ref={inputRef}
        style={styles.hiddenInput}
        value={value}
        onChangeText={v =>
          onChange(
            v
              .toUpperCase()
              .replace(/[^A-Z0-9]/g, '')
              .slice(0, CODE_LENGTH),
          )
        }
        autoCapitalize="characters"
        maxLength={CODE_LENGTH}
        testID="hidden-code-input"
      />
    </TouchableOpacity>
  );
}

type JoinResult = {
  group: {
    id: string;
    name: string;
    contribution_amount_cedis: string;
    frequency: string;
    target_member_count: number;
    current_member_count: number;
    status: string;
  };
  membership: { id: string; status: string };
} | null;

type ErrorState = { code: string; message: string } | null;

export function JoinSusuScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();

  const [code, setCode] = useState('');
  const [joining, setJoining] = useState(false);
  const [result, setResult] = useState<JoinResult>(null);
  const [error, setError] = useState<ErrorState>(null);

  const idempotencyKey = useRef<string>(`join-susu-${Date.now()}-${Math.random()}`);

  const handleJoin = useCallback(async () => {
    if (code.length !== CODE_LENGTH) return;
    setJoining(true);
    setResult(null);
    setError(null);

    try {
      const data = await susuApi.joinGroup(code, idempotencyKey.current);
      setResult(data);
    } catch (e) {
      console.error(e);
      const err = e as { code?: string; message?: string; status?: number } | null;
      const errCode = err?.code ?? '';
      if (errCode === 'SUSU_GROUP_FULL') {
        setError({ code: errCode, message: 'This susu is full — no more members can join.' });
      } else if (errCode === 'SUSU_ALREADY_A_MEMBER') {
        setError({ code: errCode, message: "You're already a member of this group." });
      } else if (err?.status === 404) {
        setError({
          code: 'NOT_FOUND',
          message: 'No susu found with that code. Check the code and try again.',
        });
      } else if (errCode === 'SUSU_GROUP_NOT_JOINABLE') {
        setError({
          code: errCode,
          message: 'This susu has already started and cannot be joined.',
        });
      } else {
        setError({
          code: 'UNKNOWN',
          message: err?.message ?? 'Something went wrong. Please try again.',
        });
      }
    } finally {
      setJoining(false);
    }
  }, [code]);

  useEffect(() => {
    if (code.length === CODE_LENGTH) {
      handleJoin();
    }
    if (code.length < CODE_LENGTH) {
      setResult(null);
      setError(null);
    }
  }, [code, handleJoin]);

  const handleConfirmNavigate = () => {
    if (!result) return;
    navigation.navigate('SusuDetail', { groupId: result.group.id });
  };

  const handleAlreadyMember = () => {
    if (!result) return;
    navigation.navigate('SusuDetail', { groupId: result.group.id });
  };

  function formatFreq(f: string) {
    return f === 'BIWEEKLY' ? 'Every 2 weeks' : f === 'WEEKLY' ? 'Weekly' : 'Monthly';
  }

  const isAlreadyMember = error?.code === 'SUSU_ALREADY_A_MEMBER';

  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
      testID="join-susu-screen"
    >
      <Text style={styles.heading}>Join a susu</Text>
      <Text style={styles.subheading}>Enter the 8-character code from the group organiser.</Text>

      <CodeInput value={code} onChange={setCode} />

      {joining && (
        <View style={styles.center} testID="joining-indicator">
          <ActivityIndicator color="#111827" />
          <Text style={styles.joiningText}>{'Looking up group…'}</Text>
        </View>
      )}

      {error && !joining && (
        <View style={[styles.previewCard, styles.errorCard]} testID="join-error">
          <Text style={styles.errorCardText}>{error.message}</Text>
          {isAlreadyMember && (
            <TouchableOpacity onPress={handleAlreadyMember} testID="view-group-btn">
              <Text style={styles.errorCardLink}>{'View group →'}</Text>
            </TouchableOpacity>
          )}
        </View>
      )}

      {result && !joining && (
        <View style={styles.previewCard} testID="join-preview">
          <Text style={styles.previewName}>{result.group.name}</Text>

          <View style={styles.previewRow}>
            <Text style={styles.previewKey}>Contribution</Text>
            <Text style={styles.previewValue}>
              {'GHS '}
              {result.group.contribution_amount_cedis}
              {' / round'}
            </Text>
          </View>
          <View style={styles.previewRow}>
            <Text style={styles.previewKey}>Frequency</Text>
            <Text style={styles.previewValue}>{formatFreq(result.group.frequency)}</Text>
          </View>
          <View style={styles.previewRow}>
            <Text style={styles.previewKey}>Members</Text>
            <Text style={styles.previewValue}>
              {result.group.current_member_count} / {result.group.target_member_count}
            </Text>
          </View>
          <View style={styles.previewRow}>
            <Text style={styles.previewKey}>Status</Text>
            <Text
              style={[
                styles.previewValue,
                result.group.status === 'PENDING' ? styles.statusPending : styles.statusActive,
              ]}
            >
              {result.group.status === 'PENDING' ? 'Waiting to start' : 'Active'}
            </Text>
          </View>

          <View style={styles.joinedBadge} testID="joined-badge">
            <Text style={styles.joinedBadgeText}>{"✓ You've joined!"}</Text>
          </View>

          <TouchableOpacity
            style={styles.viewGroupBtn}
            onPress={handleConfirmNavigate}
            testID="view-group-btn"
          >
            <Text style={styles.viewGroupBtnText}>{'View group →'}</Text>
          </TouchableOpacity>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F9FAFB' },
  content: { padding: 24, paddingBottom: 40 },
  heading: { fontSize: 24, fontWeight: '800', color: '#111827', marginBottom: 8 },
  subheading: { fontSize: 15, color: '#6B7280', marginBottom: 32 },

  codeRow: { flexDirection: 'row', justifyContent: 'center', gap: 8, marginBottom: 24 },
  codeBox: {
    width: 36,
    height: 48,
    borderRadius: 8,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  codeBoxEmpty: { borderColor: '#E5E7EB', backgroundColor: '#FFFFFF' },
  codeBoxFilled: { borderColor: '#111827', backgroundColor: '#F3F4F6' },
  codeBoxCursor: { borderColor: '#111827' },
  codeChar: { fontSize: 20, fontWeight: '800', color: '#111827' },
  hiddenInput: { position: 'absolute', opacity: 0, width: 1, height: 1 },

  center: { alignItems: 'center', paddingVertical: 24 },
  joiningText: { color: '#6B7280', marginTop: 8, fontSize: 13 },

  previewCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 20,
    shadowColor: '#000',
    shadowOpacity: 0.06,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 2 },
    elevation: 2,
  },
  errorCard: { backgroundColor: '#FEF2F2', borderWidth: 1, borderColor: '#FECACA' },
  errorCardText: { color: '#991B1B', fontSize: 14, marginBottom: 8 },
  errorCardLink: { color: '#1D4ED8', fontWeight: '600', fontSize: 14 },

  previewName: { fontSize: 20, fontWeight: '800', color: '#111827', marginBottom: 16 },
  previewRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
  },
  previewKey: { fontSize: 13, color: '#6B7280' },
  previewValue: { fontSize: 13, fontWeight: '600', color: '#111827' },
  statusPending: { color: '#92400E' },
  statusActive: { color: '#065F46' },

  joinedBadge: {
    backgroundColor: '#D1FAE5',
    borderRadius: 8,
    padding: 12,
    alignItems: 'center',
    marginTop: 16,
    marginBottom: 12,
  },
  joinedBadgeText: { color: '#065F46', fontWeight: '700', fontSize: 15 },

  viewGroupBtn: {
    backgroundColor: '#111827',
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
  },
  viewGroupBtnText: { color: '#FFFFFF', fontWeight: '700' },
});
