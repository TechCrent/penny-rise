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
import { Ionicons } from '@expo/vector-icons';
import { useCreateVault } from '../../api/hooks/useCreateVault';
import { useVaults } from '../../api/hooks/useVaults';
import { extractApiError } from '../../api/client';
import { RootStackParamList } from '../../navigation/RootNavigator';
import { VaultTypeCard } from './components/VaultTypeCard';
import { PressableScale } from '../../components/ui';
import { colors, radii, spacing, typography } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'CreateVault'>;

const FREE_TIER_MAX_STANDARD = 2;
const FREE_TIER_MAX_LOCKED = 1;

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
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export default function CreateVaultScreen() {
  const navigation = useNavigation<Nav>();
  const { mutateAsync, isPending } = useCreateVault();
  const { data: vaultData } = useVaults();

  const idempotencyKeyRef = useRef<string>(generateIdempotencyKey());

  const [step, setStep] = useState<Step>(1);
  const [name, setName] = useState('');
  const [vaultType, setVaultType] = useState<VaultType>('STANDARD');
  const [unlockDate, setUnlockDate] = useState('');
  const [unlockAmount, setUnlockAmount] = useState('');
  const [condLogic, setCondLogic] = useState<'AND' | 'OR'>('AND');
  const [serverError, setServerError] = useState<string | null>(null);
  const [upgradeUrl, setUpgradeUrl] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const vaults = vaultData?.vaults ?? [];
  const standardCount = vaults.filter(
    v => v.vault_type === 'STANDARD' && v.status !== 'CLOSED',
  ).length;
  const lockedCount = vaults.filter(v => v.vault_type === 'LOCKED' && v.status !== 'CLOSED').length;
  const standardLimited = standardCount >= FREE_TIER_MAX_STANDARD;
  const lockedLimited = lockedCount >= FREE_TIER_MAX_LOCKED;
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
    const hasDate = unlockDate.trim().length > 0;
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

    const hasDate = unlockDate.trim().length > 0;
    const hasAmount = unlockAmount.trim().length > 0;

    const payload = {
      name: name.trim(),
      vault_type: vaultType,
      ...(vaultType === 'LOCKED' && {
        unlock_at: hasDate ? toISODateString(unlockDate) : null,
        unlock_amount: hasAmount ? Math.round(parseFloat(unlockAmount) * 100) : null,
        unlock_condition_logic: hasDate && hasAmount ? condLogic : null,
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
      console.error(err);
      const apiError = extractApiError(err);
      const status = axios.isAxiosError(err) ? err.response?.status : undefined;
      setUpgradeUrl(null);

      // v0.5-030 renamed this backend error code from VAULT_FREE_TIER_LIMIT_REACHED
      // to VAULT_TIER_LIMIT_EXCEEDED — this branch never matched the real
      // response after that rename, so the upgrade prompt below never showed.
      if (status === 422 && apiError?.code === 'VAULT_TIER_LIMIT_EXCEEDED') {
        setServerError(
          "You've reached the free-tier limit for this vault type. Upgrade to Premium to create more.",
        );
        setUpgradeUrl(apiError?.details?.upgrade_url ?? null);
      } else if (status === 403) {
        setServerError('Your KYC verification must be approved before creating a vault.');
      } else if (apiError?.message) {
        setServerError(apiError.message);
      } else {
        const fallbackMessage = axios.isAxiosError(err)
          ? (err.response?.data as { message?: string } | undefined)?.message
          : undefined;
        setServerError(
          fallbackMessage ?? "Something went wrong. Your vault wasn't created — please try again.",
        );
      }
    }
  }

  function renderStep2() {
    const hasDate = unlockDate.trim().length > 0;
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
          Choose at least one condition. When it&apos;s met, your vault unlocks and you can withdraw
          freely with no penalty.
        </Text>

        <View style={styles.warningBox}>
          <Ionicons name="warning-outline" size={18} color={colors.status.warningText} />
          <View style={styles.warningBody}>
            <Text style={styles.warningTitle}>Early exit penalty</Text>
            <Text style={styles.warningDesc}>
              Breaking the lock before conditions are met incurs a{' '}
              <Text style={styles.warningBold}>5% penalty</Text> on the vault balance at the time of
              exit.
            </Text>
          </View>
        </View>

        <Text style={styles.fieldLabel}>
          Unlock date <Text style={styles.optional}>(optional)</Text>
        </Text>
        <TextInput
          style={[styles.input, fieldErrors.unlockDate ? styles.inputError : null]}
          placeholder="dd/mm/yyyy"
          placeholderTextColor={colors.textTertiary}
          value={unlockDate}
          onChangeText={t => {
            setUnlockDate(t);
            setFieldErrors(e => ({ ...e, unlockDate: '' }));
          }}
          keyboardType="numbers-and-punctuation"
          returnKeyType="done"
          accessibilityLabel="Unlock date"
        />
        {!!fieldErrors.unlockDate && <Text style={styles.errorText}>{fieldErrors.unlockDate}</Text>}

        <Text style={[styles.fieldLabel, styles.fieldLabelMt16]}>
          Target amount (GHS) <Text style={styles.optional}>(optional)</Text>
        </Text>
        <TextInput
          style={[styles.input, fieldErrors.unlockAmount ? styles.inputError : null]}
          placeholder="e.g. 5000.00"
          placeholderTextColor={colors.textTertiary}
          value={unlockAmount}
          onChangeText={t => {
            setUnlockAmount(t);
            setFieldErrors(e => ({ ...e, unlockAmount: '' }));
          }}
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
                  <Text
                    style={[
                      styles.logicOptionText,
                      condLogic === opt && styles.logicOptionTextActive,
                    ]}
                  >
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
            onPress={() => (step === 2 ? setStep(1) : navigation.goBack())}
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
                placeholderTextColor={colors.textTertiary}
                value={name}
                onChangeText={t => {
                  setName(t);
                  setFieldErrors(e => ({ ...e, name: '' }));
                }}
                maxLength={100}
                returnKeyType="done"
                autoFocus
                accessibilityLabel="Vault name"
              />
              {!!fieldErrors.name && <Text style={styles.errorText}>{fieldErrors.name}</Text>}
              <Text style={styles.charCount}>{name.length}/100</Text>

              <Text style={[styles.fieldLabel, styles.fieldLabelVaultType]}>Vault type</Text>

              <VaultTypeCard
                type="STANDARD"
                selected={vaultType === 'STANDARD'}
                onSelect={() => {
                  setVaultType('STANDARD');
                  setServerError(null);
                }}
                disabled={standardLimited && vaultType !== 'STANDARD'}
                limitReached={standardLimited}
              />

              <VaultTypeCard
                type="LOCKED"
                selected={vaultType === 'LOCKED'}
                onSelect={() => {
                  setVaultType('LOCKED');
                  setServerError(null);
                }}
                disabled={lockedLimited && vaultType !== 'LOCKED'}
                limitReached={lockedLimited}
              />
            </>
          ) : (
            renderStep2()
          )}

          {!!serverError && (
            <View style={styles.serverErrorBox}>
              <Text style={styles.serverErrorText}>{serverError}</Text>
            </View>
          )}

          {!!upgradeUrl && (
            <TouchableOpacity
              style={styles.upgradeLink}
              onPress={() => navigation.navigate('SubscriptionUpgrade')}
              accessibilityRole="button"
              accessibilityLabel="Upgrade to Premium"
            >
              <Text style={styles.upgradeLinkText}>Upgrade to Premium →</Text>
            </TouchableOpacity>
          )}

          {step === 1 && selectedLimited && (
            <View style={styles.upgradeBanner}>
              <Text style={styles.upgradeText}>
                You&apos;ve used your free {vaultType === 'STANDARD' ? 'standard' : 'locked'} vault
                allowance. Upgrade to Premium for unlimited vaults.
              </Text>
            </View>
          )}

          <PressableScale
            style={[
              styles.ctaButton,
              (isPending || (step === 1 && selectedLimited)) && styles.ctaDisabled,
            ]}
            onPress={step === 1 ? handleNext : handleSubmit}
            disabled={isPending || (step === 1 && selectedLimited)}
            accessibilityRole="button"
            accessibilityState={{
              disabled: isPending || (step === 1 && selectedLimited),
              busy: isPending,
            }}
            accessibilityLabel={
              step === 1
                ? vaultType === 'LOCKED'
                  ? 'Next — set unlock conditions'
                  : 'Create standard vault'
                : 'Create locked vault'
            }
          >
            {isPending ? (
              <ActivityIndicator color={colors.neutral[900]} size="small" />
            ) : (
              <Text style={styles.ctaText}>
                {step === 1 ? (vaultType === 'LOCKED' ? 'Next' : 'Create vault') : 'Create vault'}
              </Text>
            )}
          </PressableScale>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  flex: { flex: 1 },
  scroll: { flex: 1 },
  content: { paddingHorizontal: spacing.lg, paddingTop: spacing.md, paddingBottom: spacing['6xl'] },

  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    backgroundColor: colors.background,
  },
  backButton: { width: 40, height: 40, justifyContent: 'center' },
  backIcon: { fontSize: 22, color: colors.textPrimary },
  headerTitle: { ...typography.h3, color: colors.textPrimary },

  stepIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: spacing.xl,
    marginTop: spacing.xxs,
  },
  stepDot: { width: 10, height: 10, borderRadius: 5, backgroundColor: colors.neutral[300] },
  stepDotActive: { backgroundColor: colors.gold.base, width: 12, height: 12, borderRadius: 6 },
  stepDotDone: { backgroundColor: colors.status.success },
  stepLine: { flex: 1, height: 2, backgroundColor: colors.border, marginHorizontal: spacing.sm },

  stepTitle: { ...typography.h2, color: colors.textPrimary, marginBottom: spacing.xs },
  stepSubtitle: { fontSize: 14, color: colors.textSecondary, lineHeight: 20, marginBottom: spacing.xl },
  fieldLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  optional: { fontWeight: '400', color: colors.textSecondary, textTransform: 'none' },
  fieldLabelMt16: { marginTop: spacing.lg },
  fieldLabelVaultType: { marginTop: spacing['2xl'], marginBottom: spacing.md },
  charCount: { fontSize: 12, color: colors.textTertiary, textAlign: 'right', marginTop: spacing.xs },

  input: {
    backgroundColor: colors.surface,
    borderWidth: 1.5,
    borderColor: colors.borderStrong,
    borderRadius: radii.md,
    paddingHorizontal: spacing.md,
    paddingVertical: Platform.OS === 'ios' ? 14 : 11,
    fontSize: 15,
    color: colors.textPrimary,
  },
  inputError: { borderColor: colors.status.error },

  errorText: { fontSize: 12, color: colors.status.error, marginTop: spacing.xs },
  errorBanner: { backgroundColor: colors.status.errorBg, borderRadius: radii.md, padding: spacing.md, marginTop: spacing.md },
  errorBannerText: { fontSize: 13, color: colors.status.errorText, fontWeight: '500' },

  warningBox: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.sm,
    backgroundColor: colors.status.warningBg,
    borderRadius: radii.md,
    padding: spacing.md,
    marginBottom: spacing.xl,
    borderWidth: 1,
    borderColor: '#FDE68A',
  },
  warningBody: { flex: 1 },
  warningTitle: { fontSize: 13, fontWeight: '700', color: colors.status.warningText, marginBottom: 3 },
  warningDesc: { fontSize: 13, color: '#78350F', lineHeight: 18 },
  warningBold: { fontWeight: '700' },

  logicRow: { marginTop: spacing.xl, marginBottom: spacing.xs },
  logicLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  logicToggle: { flexDirection: 'row', backgroundColor: colors.neutral[100], borderRadius: radii.md, padding: 3 },
  logicOption: { flex: 1, paddingVertical: 9, borderRadius: radii.sm, alignItems: 'center' },
  logicOptionActive: { backgroundColor: colors.gold.base },
  logicOptionText: { fontSize: 13, fontWeight: '600', color: colors.textSecondary },
  logicOptionTextActive: { color: colors.neutral[900] },

  serverErrorBox: {
    backgroundColor: colors.status.errorBg,
    borderRadius: radii.md,
    padding: spacing.md,
    marginTop: spacing.lg,
    borderWidth: 1,
    borderColor: '#FCA5A5',
  },
  serverErrorText: { fontSize: 13, color: colors.status.errorText, lineHeight: 19 },
  upgradeLink: { marginTop: spacing.sm, alignItems: 'center', paddingVertical: spacing.xs },
  upgradeLinkText: { fontSize: 14, fontWeight: '700', color: colors.gold.text },

  upgradeBanner: {
    backgroundColor: colors.status.infoBg,
    borderRadius: radii.md,
    padding: spacing.md,
    marginTop: spacing.sm,
    marginBottom: spacing.sm,
  },
  upgradeText: { fontSize: 13, color: colors.status.infoText, lineHeight: 19 },

  ctaButton: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingVertical: 15,
    alignItems: 'center',
    marginTop: spacing.xl,
  },
  ctaDisabled: { backgroundColor: colors.neutral[300] },
  ctaText: { fontSize: 16, fontWeight: '700', color: colors.neutral[900] },
});
