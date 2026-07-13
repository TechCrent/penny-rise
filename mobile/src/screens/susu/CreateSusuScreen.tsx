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
import { PressableScale } from '../../components/ui';
import { colors, radii, spacing } from '../../theme';
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
          <Text style={styles.label}>Susu type</Text>
          <View style={styles.pillRow} testID="group-type-picker">
            <TouchableOpacity
              style={[styles.pill, styles.pillActive]}
              testID="group-type-traditional"
              accessibilityState={{ selected: true }}
            >
              <Text style={[styles.pillText, styles.pillTextActive]}>Traditional</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.pill}
              onPress={() => navigation.navigate('SusuModernComingSoon')}
              testID="group-type-modern"
              accessibilityState={{ selected: false }}
              accessibilityLabel="Modern — coming soon"
            >
              <Text style={styles.pillText}>Modern</Text>
            </TouchableOpacity>
          </View>
          <Text style={styles.hint}>
            Traditional: interest-free rotating credit — each round, one member gets the pot.
          </Text>
        </View>

        <View style={styles.field}>
          <Text style={styles.label}>Group name</Text>
          <TextInput
            style={[styles.input, errors.name && styles.inputError]}
            value={form.name}
            onChangeText={v => update('name', v)}
            placeholder="e.g. Akua's Savings Circle"
            placeholderTextColor={colors.textTertiary}
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
            placeholderTextColor={colors.textTertiary}
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

        <PressableScale
          style={[styles.submitBtn, submitting && styles.submitBtnDisabled]}
          onPress={handleSubmit}
          disabled={submitting}
          testID="submit-btn"
        >
          {submitting ? (
            <ActivityIndicator color={colors.neutral[900]} />
          ) : (
            <Text style={styles.submitBtnText}>{'Next: invite members →'}</Text>
          )}
        </PressableScale>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  screen: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.xl, paddingBottom: spacing['4xl'] },
  stepRow: { marginBottom: spacing['2xl'] },
  stepLabel: {
    fontSize: 12,
    color: colors.textSecondary,
    fontWeight: '600',
    marginBottom: spacing.xs,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  progressBg: { height: 4, backgroundColor: colors.neutral[200], borderRadius: 2 },
  progressFill: { height: 4, backgroundColor: colors.gold.base, borderRadius: 2, width: '33%' },
  field: { marginBottom: spacing.xl },
  label: { fontSize: 14, fontWeight: '600', color: colors.neutral[700], marginBottom: spacing.sm },
  labelSub: { fontWeight: '400', color: colors.textTertiary },
  input: {
    backgroundColor: colors.surface,
    borderWidth: 1.5,
    borderColor: colors.border,
    borderRadius: radii.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    fontSize: 16,
    color: colors.textPrimary,
  },
  inputError: { borderColor: colors.status.error },
  errorText: { color: colors.status.error, fontSize: 12, marginTop: spacing.xs },
  hint: { color: colors.textTertiary, fontSize: 12, marginTop: spacing.xs },
  pillRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  pill: {
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
    borderRadius: radii.pill,
    borderWidth: 1.5,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  pillSmall: { paddingHorizontal: spacing.md, paddingVertical: spacing.sm },
  pillActive: { backgroundColor: colors.gold.base, borderColor: colors.gold.base },
  pillText: { fontSize: 14, fontWeight: '500', color: colors.neutral[700] },
  pillTextActive: { color: colors.neutral[900] },
  helperBox: { backgroundColor: colors.status.infoBg, borderRadius: radii.md, padding: spacing.md, marginBottom: spacing['2xl'] },
  helperText: { color: colors.status.infoText, fontSize: 13 },
  submitBtn: {
    backgroundColor: colors.gold.base,
    paddingVertical: spacing.lg,
    borderRadius: radii.md,
    alignItems: 'center',
  },
  submitBtnDisabled: { opacity: 0.5 },
  submitBtnText: { color: colors.neutral[900], fontSize: 16, fontWeight: '700' },
});
