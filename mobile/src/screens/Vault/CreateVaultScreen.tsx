import React, { useRef, useState } from 'react';
import {
  View,
  Text,
  TextInput,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import axios from 'axios';
import { useCreateVault } from '../../api/hooks/useCreateVault';
import { useVaults } from '../../api/hooks/useVaults';
import { extractApiError } from '../../api/client';
import { RootStackParamList } from '../../navigation/RootNavigator';
import { VaultTypeCard } from './components/VaultTypeCard';

type Nav = NativeStackNavigationProp<RootStackParamList, 'CreateVault'>;

const FREE_TIER_MAX_STANDARD = 2;
const FREE_TIER_MAX_LOCKED   = 1;

type Step = 1 | 2;
type VaultType = 'STANDARD' | 'LOCKED';

function toISODateString(input: string): string | null {
  const parts = input.split('/');
  if (parts.length !== 3) return null;
  const [day, month, year] = parts;
  const d = new Date(`${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}T00:00:00Z`);
  if (isNaN(d.getTime())) return null;
  if (d <= new Date()) return null;
  return d.toISOString();
}

function generateIdempotencyKey(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0;
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
  });
}

export default function CreateVaultScreen() {
  const navigation = useNavigation<Nav>();
  const { mutateAsync, isPending } = useCreateVault();
  const { data: vaultData }        = useVaults();

  const idempotencyKeyRef = useRef<string>(generateIdempotencyKey());

  const [step,         setStep]         = useState<Step>(1);
  const [name,         setName]         = useState('');
  const [vaultType,    setVaultType]    = useState<VaultType>('STANDARD');
  const [unlockDate,   setUnlockDate]   = useState('');
  const [unlockAmount, setUnlockAmount] = useState('');
  const [condLogic,    setCondLogic]    = useState<'AND' | 'OR'>('AND');
  const [serverError,  setServerError]  = useState<string | null>(null);
  const [fieldErrors,  setFieldErrors]  = useState<Record<string, string>>({});

  const vaults          = vaultData?.vaults ?? [];
  const standardCount   = vaults.filter(v => v.vault_type === 'STANDARD' && v.status !== 'CLOSED').length;
  const lockedCount     = vaults.filter(v => v.vault_type === 'LOCKED'   && v.status !== 'CLOSED').length;
  const standardLimited = standardCount >= FREE_TIER_MAX_STANDARD;
  const lockedLimited   = lockedCount   >= FREE_TIER_MAX_LOCKED;
  const selectedLimited = vaultType === 'STANDARD' ? standardLimited : lockedLimited;

  function validateStep1(): boolean {
    const errs: Record<string, string> = {};
    if (!name.trim()) errs.name = 'Vault name is required.';
    if (name.trim().length > 100) errs.name = 'Name must be 100 characters or fewer.';
    setFieldErrors(errs);
    return Object.keys(errs).length === 0;
  }

  function validateStep2(): boolean {
    const errs: Record<string, string> = {};
    const hasDate   = unlockDate.trim().length > 0;
    const hasAmount = unlockAmount.trim().length > 0;

    if (!hasDate && !hasAmount) {
      errs.unlock = 'At least one unlock condition is required for a locked vault.';
    }
    if (hasDate && !toISODateString(unlockDate)) {
      errs.unlockDate = 'Enter a valid future date in dd/mm/yyyy format.';
    }
    if (hasAmount) {
      const parsed = parseFloat(unlockAmount);
      if (isNaN(parsed) || parsed <= 0) {
        errs.unlockAmount = 'Enter a valid amount greater than 0.';
      }
    }
    setFieldErrors(errs);
    return Object.keys(errs).length === 0;
  }

  function handleNext() {
    if (!validateStep1()) return;
    if (vaultType === 'STANDARD') {
      handleSubmit();
    } else {
      setStep(2);
    }
  }

  async function handleSubmit() {
    if (vaultType === 'LOCKED' && !validateStep2()) return;
    setServerError(null);

    const hasDate   = unlockDate.trim().length > 0;
    const hasAmount = unlockAmount.trim().length > 0;

    const payload = {
      name: name.trim(),
      vault_type: vaultType,
      ...(vaultType === 'LOCKED' && {
        unlock_at:               hasDate   ? toISODateString(unlockDate) : null,
        unlock_amount:           hasAmount ? Math.round(parseFloat(unlockAmount) * 100) : null,
        unlock_condition_logic:  (hasDate && hasAmount) ? condLogic : null,
      }),
    };

    try {
      const created = await mutateAsync({
        payload,
        idempotencyKey: idempotencyKeyRef.current,
      });
      idempotencyKeyRef.current = generateIdempotencyKey();
      navigation.replace('VaultDetail', {
        vaultId: created.id,
        successMessage: `${created.name} created successfully!`,
      });
    } catch (err: unknown) {
      const apiError = extractApiError(err);
      const status   = axios.isAxiosError(err) ? err.response?.status : undefined;

      if (status === 422 && apiError?.code === 'VAULT_FREE_TIER_LIMIT_REACHED') {
        setServerError("You've reached the free-tier limit for this vault type. Upgrade to Premium to create more.");
      } else if (status === 403) {
        setServerError('Your KYC verification must be approved before creating a vault.');
      } else if (apiError?.message) {
        setServerError(apiError.message);
      } else {
        const fallbackMessage = axios.isAxiosError(err)
          ? (err.response?.data as { message?: string } | undefined)?.message
          : undefined;
        setServerError(fallbackMessage ?? "Something went wrong. Your vault wasn't created — please try again.");
      }
    }
  }

  function renderStep2() {
    const hasDate   = unlockDate.trim().length > 0;
    const hasAmount = unlockAmount.trim().length > 0;

    return (
      <>
        <View style={styles.stepIndicator}>
          <View style={[styles.stepDot, styles.stepDotDone]} />
          <View style={styles.stepLine} />
          <View style={[styles.stepDot, styles.stepDotActive]} />
        </View>

        <Text style={styles.stepTitle}>Set unlock conditions</Text>
        <Text style={styles.stepSubtitle}>
          Choose at least one condition. When it&apos;s met, your vault unlocks and you can
          withdraw freely with no penalty.
        </Text>

        <View style={styles.warningBox}>
          <Text style={styles.warningIcon}>⚠️</Text>
          <View style={styles.warningBody}>
            <Text style={styles.warningTitle}>Early exit penalty</Text>
            <Text style={styles.warningDesc}>
              Breaking the lock before conditions are met incurs a{' '}
              <Text style={styles.warningBold}>5% penalty</Text> on the vault balance at
              the time of exit.
            </Text>
          </View>
        </View>

        <Text style={styles.fieldLabel}>
          Unlock date <Text style={styles.optional}>(optional)</Text>
        </Text>
        <TextInput
          style={[styles.input, fieldErrors.unlockDate ? styles.inputError : null]}
          placeholder="dd/mm/yyyy"
          value={unlockDate}
          onChangeText={t => { setUnlockDate(t); setFieldErrors(e => ({ ...e, unlockDate: '' })); }}
          keyboardType="numbers-and-punctuation"
          returnKeyType="done"
          accessibilityLabel="Unlock date"
        />
        {!!fieldErrors.unlockDate && (
          <Text style={styles.errorText}>{fieldErrors.unlockDate}</Text>
        )}

        <Text style={[styles.fieldLabel, styles.fieldLabelMt16]}>
          Target amount (GHS) <Text style={styles.optional}>(optional)</Text>
        </Text>
        <TextInput
          style={[styles.input, fieldErrors.unlockAmount ? styles.inputError : null]}
          placeholder="e.g. 5000.00"
          value={unlockAmount}
          onChangeText={t => { setUnlockAmount(t); setFieldErrors(e => ({ ...e, unlockAmount: '' })); }}
          keyboardType="decimal-pad"
          returnKeyType="done"
          accessibilityLabel="Target savings amount in Ghana cedis"
        />
        {!!fieldErrors.unlockAmount && (
          <Text style={styles.errorText}>{fieldErrors.unlockAmount}</Text>
        )}

        {hasDate && hasAmount && (
          <View style={styles.logicRow}>
            <Text style={styles.logicLabel}>Unlock when:</Text>
            <View style={styles.logicToggle}>
              {(['AND', 'OR'] as const).map(opt => (
                <TouchableOpacity
                  key={opt}
                  style={[styles.logicOption, condLogic === opt && styles.logicOptionActive]}
                  onPress={() => setCondLogic(opt)}
                  accessibilityRole="radio"
                  accessibilityState={{ selected: condLogic === opt }}
                >
                  <Text style={[styles.logicOptionText, condLogic === opt && styles.logicOptionTextActive]}>
                    {opt === 'AND' ? 'Both conditions' : 'Either condition'}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>
        )}

        {!!fieldErrors.unlock && (
          <View style={styles.errorBanner}>
            <Text style={styles.errorBannerText}>{fieldErrors.unlock}</Text>
          </View>
        )}
      </>
    );
  }

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        <View style={styles.header}>
          <TouchableOpacity
            onPress={() => step === 2 ? setStep(1) : navigation.goBack()}
            accessibilityLabel={step === 2 ? 'Back to step 1' : 'Cancel'}
            style={styles.backButton}
          >
            <Text style={styles.backIcon}>←</Text>
          </TouchableOpacity>
          <Text style={styles.headerTitle}>
            {step === 1 ? 'Create a vault' : 'Unlock conditions'}
          </Text>
          <View style={styles.backButton} />
        </View>

        <ScrollView
          style={styles.scroll}
          contentContainerStyle={styles.content}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          {step === 1 ? (
            <>
              <View style={styles.stepIndicator}>
                <View style={[styles.stepDot, styles.stepDotActive]} />
                <View style={styles.stepLine} />
                <View style={styles.stepDot} />
              </View>

              <Text style={styles.stepTitle}>Name your vault</Text>
              <Text style={styles.fieldLabel}>Vault name</Text>
              <TextInput
                style={[styles.input, fieldErrors.name ? styles.inputError : null]}
                placeholder="e.g. Emergency fund, School fees"
                value={name}
                onChangeText={t => { setName(t); setFieldErrors(e => ({ ...e, name: '' })); }}
                maxLength={100}
                returnKeyType="done"
                autoFocus
                accessibilityLabel="Vault name"
              />
              {!!fieldErrors.name && (
                <Text style={styles.errorText}>{fieldErrors.name}</Text>
              )}
              <Text style={styles.charCount}>{name.length}/100</Text>

              <Text style={[styles.fieldLabel, styles.fieldLabelVaultType]}>
                Vault type
              </Text>

              <VaultTypeCard
                type="STANDARD"
                selected={vaultType === 'STANDARD'}
                onSelect={() => { setVaultType('STANDARD'); setServerError(null); }}
                disabled={standardLimited && vaultType !== 'STANDARD'}
                limitReached={standardLimited}
              />

              <VaultTypeCard
                type="LOCKED"
                selected={vaultType === 'LOCKED'}
                onSelect={() => { setVaultType('LOCKED'); setServerError(null); }}
                disabled={lockedLimited && vaultType !== 'LOCKED'}
                limitReached={lockedLimited}
              />
            </>
          ) : renderStep2()}

          {!!serverError && (
            <View style={styles.serverErrorBox}>
              <Text style={styles.serverErrorText}>{serverError}</Text>
            </View>
          )}

          {step === 1 && selectedLimited && (
            <View style={styles.upgradeBanner}>
              <Text style={styles.upgradeText}>
                You&apos;ve used your free {vaultType === 'STANDARD' ? 'standard' : 'locked'} vault
                allowance. Upgrade to Premium for unlimited vaults.
              </Text>
            </View>
          )}

          <TouchableOpacity
            style={[
              styles.ctaButton,
              (isPending || (step === 1 && selectedLimited)) && styles.ctaDisabled,
            ]}
            onPress={step === 1 ? handleNext : handleSubmit}
            disabled={isPending || (step === 1 && selectedLimited)}
            activeOpacity={0.85}
            accessibilityRole="button"
            accessibilityState={{
              disabled: isPending || (step === 1 && selectedLimited),
              busy: isPending,
            }}
            accessibilityLabel={
              step === 1
                ? vaultType === 'LOCKED' ? 'Next — set unlock conditions' : 'Create standard vault'
                : 'Create locked vault'
            }
          >
            {isPending ? (
              <ActivityIndicator color="#FFFFFF" size="small" />
            ) : (
              <Text style={styles.ctaText}>
                {step === 1
                  ? vaultType === 'LOCKED' ? 'Next' : 'Create vault'
                  : 'Create vault'}
              </Text>
            )}
          </TouchableOpacity>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const INDIGO     = '#4F46E5';
const DARK       = '#1A1A2E';
const MUTED      = '#6B7280';
const BACKGROUND = '#F8F9FF';
const AMBER      = '#D97706';

const styles = StyleSheet.create({
  safe:    { flex: 1, backgroundColor: BACKGROUND },
  flex:    { flex: 1 },
  scroll:  { flex: 1 },
  content: { paddingHorizontal: 16, paddingTop: 12, paddingBottom: 60 },

  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#EDEDF0',
    backgroundColor: BACKGROUND,
  },
  backButton:  { width: 40, height: 40, justifyContent: 'center' },
  backIcon:    { fontSize: 22, color: DARK },
  headerTitle: { fontSize: 17, fontWeight: '700', color: DARK, letterSpacing: -0.2 },

  stepIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 20,
    marginTop: 4,
  },
  stepDot:       { width: 10, height: 10, borderRadius: 5, backgroundColor: '#D1D5DB' },
  stepDotActive: { backgroundColor: INDIGO, width: 12, height: 12, borderRadius: 6 },
  stepDotDone:   { backgroundColor: '#10B981' },
  stepLine:      { flex: 1, height: 2, backgroundColor: '#E5E7EB', marginHorizontal: 8 },

  stepTitle:    { fontSize: 20, fontWeight: '700', color: DARK, marginBottom: 4, letterSpacing: -0.3 },
  stepSubtitle: { fontSize: 14, color: MUTED, lineHeight: 20, marginBottom: 20 },
  fieldLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: DARK,
    marginBottom: 6,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  optional:           { fontWeight: '400', color: MUTED, textTransform: 'none' },
  fieldLabelMt16:     { marginTop: 16 },
  fieldLabelVaultType: { marginTop: 24, marginBottom: 12 },
  charCount: { fontSize: 12, color: '#9CA3AF', textAlign: 'right', marginTop: 4 },

  input: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1.5,
    borderColor: '#D1D5DB',
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: Platform.OS === 'ios' ? 14 : 11,
    fontSize: 15,
    color: DARK,
  },
  inputError: { borderColor: '#EF4444' },

  errorText: { fontSize: 12, color: '#DC2626', marginTop: 4 },
  errorBanner: { backgroundColor: '#FEF2F2', borderRadius: 10, padding: 12, marginTop: 12 },
  errorBannerText: { fontSize: 13, color: '#991B1B', fontWeight: '500' },

  warningBox: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 10,
    backgroundColor: '#FFFBEB',
    borderRadius: 12,
    padding: 14,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#FDE68A',
  },
  warningIcon:  { fontSize: 18, lineHeight: 22 },
  warningBody:  { flex: 1 },
  warningTitle: { fontSize: 13, fontWeight: '700', color: AMBER, marginBottom: 3 },
  warningDesc:  { fontSize: 13, color: '#78350F', lineHeight: 18 },
  warningBold:  { fontWeight: '700' },

  logicRow:    { marginTop: 20, marginBottom: 4 },
  logicLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: DARK,
    marginBottom: 8,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  logicToggle:           { flexDirection: 'row', backgroundColor: '#F3F4F6', borderRadius: 10, padding: 3 },
  logicOption:           { flex: 1, paddingVertical: 9, borderRadius: 8, alignItems: 'center' },
  logicOptionActive:     { backgroundColor: INDIGO },
  logicOptionText:       { fontSize: 13, fontWeight: '600', color: MUTED },
  logicOptionTextActive: { color: '#FFFFFF' },

  serverErrorBox: {
    backgroundColor: '#FEF2F2',
    borderRadius: 12,
    padding: 14,
    marginTop: 16,
    borderWidth: 1,
    borderColor: '#FCA5A5',
  },
  serverErrorText: { fontSize: 13, color: '#991B1B', lineHeight: 19 },

  upgradeBanner: { backgroundColor: '#EFF6FF', borderRadius: 12, padding: 14, marginTop: 8, marginBottom: 8 },
  upgradeText:   { fontSize: 13, color: '#1E40AF', lineHeight: 19 },

  ctaButton: {
    backgroundColor: INDIGO,
    borderRadius: 14,
    paddingVertical: 15,
    alignItems: 'center',
    marginTop: 24,
    shadowColor: INDIGO,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.28,
    shadowRadius: 10,
    elevation: 4,
  },
  ctaDisabled: { backgroundColor: '#A5B4FC', shadowOpacity: 0, elevation: 0 },
  ctaText:     { fontSize: 16, fontWeight: '700', color: '#FFFFFF' },
});
