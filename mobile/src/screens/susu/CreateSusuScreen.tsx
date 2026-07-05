import React, { useState, useRef, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { susuApi } from '../../api/susu';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import {
  validateCreateSusuForm,
  cedisToPesewas,
  type CreateSusuForm,
  type CreateSusuErrors,
} from './createSusuValidation';

const FREQUENCIES: Array<{ value: CreateSusuForm['frequency']; label: string }> = [
  { value: 'WEEKLY', label: 'Weekly' },
  { value: 'BIWEEKLY', label: 'Every 2 wks' },
  { value: 'MONTHLY', label: 'Monthly' },
];

const MEMBER_COUNTS = [4, 5, 6, 8, 10, 12, 15, 20];

export function CreateSusuScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();

  const [form, setForm] = useState<CreateSusuForm>({
    name: '',
    contributionCedis: '',
    frequency: 'MONTHLY',
    targetMemberCount: 6,
  });
  const [errors, setErrors] = useState<CreateSusuErrors>({});
  const [submitting, setSubmitting] = useState(false);

  const idempotencyKey = useRef<string>(`create-susu-${Date.now()}-${Math.random()}`);

  const update = useCallback(<K extends keyof CreateSusuForm>(key: K, value: CreateSusuForm[K]) => {
    setForm(f => ({ ...f, [key]: value }));
    setErrors(e => ({ ...e, [key]: undefined }));
  }, []);

  const handleSubmit = useCallback(async () => {
    const validationErrors = validateCreateSusuForm(form);
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }

    setSubmitting(true);
    try {
      const result = await susuApi.createGroup(
        {
          name: form.name.trim(),
          contribution_amount: cedisToPesewas(form.contributionCedis),
          frequency: form.frequency,
          target_member_count: form.targetMemberCount,
        },
        idempotencyKey.current,
      );

      navigation.navigate('CreateSusuInvite', {
        groupId: result.id,
        joinCode: result.join_code,
        groupName: form.name.trim(),
        contributionCedis: form.contributionCedis,
        frequency: form.frequency,
        targetMemberCount: form.targetMemberCount,
      });
    } catch (e) {
      console.error(e);
      const err = e as { code?: string; message?: string } | null;
      const code = err?.code ?? '';
      if (code === 'SUSU_FREE_TIER_LIMIT_REACHED') {
        Alert.alert(
          'Group limit reached',
          'You can organise one susu group at a time on the free plan. ' +
            'Complete or cancel your current group to create a new one.',
          [{ text: 'OK' }],
        );
      } else {
        Alert.alert('Could not create group', err?.message ?? 'Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }, [form, navigation]);

  return (
    <KeyboardAvoidingView
      style={styles.flex}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        style={styles.screen}
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
        testID="create-susu-screen"
      >
        <View style={styles.stepRow}>
          <Text style={styles.stepLabel}>Step 1 of 3 · Basics</Text>
          <View style={styles.progressBg}>
            <View style={styles.progressFill} />
          </View>
        </View>

        <View style={styles.field}>
          <Text style={styles.label}>Group name</Text>
          <TextInput
            style={[styles.input, errors.name && styles.inputError]}
            value={form.name}
            onChangeText={v => update('name', v)}
            placeholder="e.g. Akua's Savings Circle"
            maxLength={100}
            testID="name-input"
          />
          {errors.name && (
            <Text style={styles.errorText} testID="name-error">
              {errors.name}
            </Text>
          )}
        </View>

        <View style={styles.field}>
          <Text style={styles.label}>Contribution per round (GHS)</Text>
          <TextInput
            style={[styles.input, errors.contributionCedis && styles.inputError]}
            value={form.contributionCedis}
            onChangeText={v => update('contributionCedis', v.replace(/[^0-9.]/g, ''))}
            placeholder="e.g. 200.00"
            keyboardType="decimal-pad"
            testID="contribution-input"
          />
          {errors.contributionCedis && (
            <Text style={styles.errorText} testID="contribution-error">
              {errors.contributionCedis}
            </Text>
          )}
          <Text style={styles.hint}>Each member pays this amount every round.</Text>
        </View>

        <View style={styles.field}>
          <Text style={styles.label}>Frequency</Text>
          <View style={styles.pillRow} testID="frequency-picker">
            {FREQUENCIES.map(f => (
              <TouchableOpacity
                key={f.value}
                style={[styles.pill, form.frequency === f.value && styles.pillActive]}
                onPress={() => update('frequency', f.value)}
                testID={`freq-${f.value}`}
              >
                <Text
                  style={[styles.pillText, form.frequency === f.value && styles.pillTextActive]}
                >
                  {f.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        <View style={styles.field}>
          <Text style={styles.label}>
            Number of members
            <Text style={styles.labelSub}> · {form.targetMemberCount} selected</Text>
          </Text>
          <View style={styles.pillRow} testID="member-count-picker">
            {MEMBER_COUNTS.map(n => (
              <TouchableOpacity
                key={n}
                style={[
                  styles.pill,
                  styles.pillSmall,
                  form.targetMemberCount === n && styles.pillActive,
                ]}
                onPress={() => update('targetMemberCount', n)}
                testID={`count-${n}`}
              >
                <Text
                  style={[styles.pillText, form.targetMemberCount === n && styles.pillTextActive]}
                >
                  {n}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
          {errors.targetMemberCount && (
            <Text style={styles.errorText}>{errors.targetMemberCount}</Text>
          )}
        </View>

        <View style={styles.helperBox}>
          <Text style={styles.helperText}>
            {"After creating, you'll share a join code with "}
            {form.targetMemberCount - 1}
            {' other members. The susu starts once everyone has joined and you activate it.'}
          </Text>
        </View>

        <TouchableOpacity
          style={[styles.submitBtn, submitting && styles.submitBtnDisabled]}
          onPress={handleSubmit}
          disabled={submitting}
          testID="submit-btn"
        >
          {submitting ? (
            <ActivityIndicator color="#FFFFFF" />
          ) : (
            <Text style={styles.submitBtnText}>{'Next: invite members →'}</Text>
          )}
        </TouchableOpacity>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  screen: { flex: 1, backgroundColor: '#F9FAFB' },
  content: { padding: 20, paddingBottom: 40 },
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
  progressFill: { height: 4, backgroundColor: '#111827', borderRadius: 2, width: '33%' },
  field: { marginBottom: 20 },
  label: { fontSize: 14, fontWeight: '600', color: '#374151', marginBottom: 8 },
  labelSub: { fontWeight: '400', color: '#9CA3AF' },
  input: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1.5,
    borderColor: '#E5E7EB',
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 16,
    color: '#111827',
  },
  inputError: { borderColor: '#EF4444' },
  errorText: { color: '#EF4444', fontSize: 12, marginTop: 4 },
  hint: { color: '#9CA3AF', fontSize: 12, marginTop: 4 },
  pillRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  pill: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 20,
    borderWidth: 1.5,
    borderColor: '#E5E7EB',
    backgroundColor: '#FFFFFF',
  },
  pillSmall: { paddingHorizontal: 12, paddingVertical: 8 },
  pillActive: { backgroundColor: '#111827', borderColor: '#111827' },
  pillText: { fontSize: 14, fontWeight: '500', color: '#374151' },
  pillTextActive: { color: '#FFFFFF' },
  helperBox: { backgroundColor: '#EFF6FF', borderRadius: 10, padding: 14, marginBottom: 24 },
  helperText: { color: '#1E40AF', fontSize: 13 },
  submitBtn: {
    backgroundColor: '#111827',
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  submitBtnDisabled: { opacity: 0.5 },
  submitBtnText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
});
