import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { extractApiError } from '../../api/client';
import { RootStackParamList } from '../../navigation/RootNavigator';
import { useAuth } from '../../hooks/useAuth';
import { useVaultDetail } from '../../api/hooks/useVaultDetail';
import {
  useRequestEarlyExit,
  useCancelEarlyExit,
  type EarlyExitReason,
  type EarlyExitResponse,
} from '../../api/hooks/useEarlyExit';
import { PROVIDERS, ProviderId, validateMomoNumber } from '../../constants/momoProviders';
import { Banner, Icon, PressableScale, ScreenHeader, type IconName } from '../../components/ui';
import { colors, radii, spacing } from '../../theme';

// ── Types ──────────────────────────────────────────────────────────────────

type Nav = NativeStackNavigationProp<RootStackParamList, 'EarlyExit'>;
type Route = RouteProp<RootStackParamList, 'EarlyExit'>;
type Phase = 'reason' | 'preview' | 'confirm' | 'done';

// Real MoMo network brand colors — kept as-is; they identify a specific
// third-party provider, not part of the app's design system.
const PROVIDER_PILL_ACTIVE: Record<ProviderId, { borderColor: string; backgroundColor: string }> = {
  mtn: { borderColor: '#FBB01C', backgroundColor: '#FAFAFA' },
  vodafone: { borderColor: '#E10A0A', backgroundColor: '#FAFAFA' },
  airteltigo: { borderColor: '#FF6200', backgroundColor: '#FAFAFA' },
};
const PROVIDER_TEXT_COLOR: Record<ProviderId, { color: string }> = {
  mtn: { color: '#FBB01C' },
  vodafone: { color: '#E10A0A' },
  airteltigo: { color: '#FF6200' },
};

// ── Reason metadata ────────────────────────────────────────────────────────

const REASON_OPTIONS: Array<{
  id: EarlyExitReason;
  label: string;
  description: string;
  icon: IconName;
}> = [
  {
    id: 'SCHOOL_FEES_EMERGENCY',
    label: 'School fees',
    description: 'Tuition or school-related payment due',
    icon: 'school-outline',
  },
  {
    id: 'MEDICAL',
    label: 'Medical emergency',
    description: 'Healthcare costs or an urgent medical need',
    icon: 'medkit-outline',
  },
  {
    id: 'FAMILY',
    label: 'Family need',
    description: 'An unexpected family obligation or expense',
    icon: 'people-outline',
  },
  {
    id: 'OTHER',
    label: 'Something else',
    description: 'Another reason not listed above',
    icon: 'ellipsis-horizontal-circle-outline',
  },
];

// ── Helpers ────────────────────────────────────────────────────────────────

function formatCedis(pesewas: number): string {
  return (pesewas / 100).toLocaleString('en-GH', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function formatDatetime(iso: string): string {
  return new Date(iso).toLocaleString('en-GH', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatCountdown(releaseAt: string): string {
  const diffMs = new Date(releaseAt).getTime() - Date.now();
  if (diffMs <= 0) return 'Processing now';
  const hours = Math.floor(diffMs / (1000 * 60 * 60));
  const minutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
  return `${hours}h ${minutes}m remaining`;
}

// ─────────────────────────────────────────────────────────────────────────────
// EarlyExitScreen
// ─────────────────────────────────────────────────────────────────────────────
export default function EarlyExitScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const { vaultId } = route.params;

  const { data: vault } = useVaultDetail(vaultId);
  const { user } = useAuth();
  const { mutateAsync: requestExit, isPending: requesting } = useRequestEarlyExit(vaultId);

  const [phase, setPhase] = useState<Phase>('reason');
  const [reason, setReason] = useState<EarlyExitReason | null>(null);
  const [momoNumber, setMomoNumber] = useState(user?.momoNumber ?? '');
  const [provider, setProvider] = useState<ProviderId>('mtn');
  const [exitResult, setExitResult] = useState<EarlyExitResponse | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);

  const momoError = momoNumber.trim().length > 0 ? validateMomoNumber(provider, momoNumber) : null;
  const canProceedFromReason = !!reason && momoNumber.trim().length > 0 && !momoError;

  // ── Submit ────────────────────────────────────────────────────────────
  const handleConfirm = useCallback(async () => {
    if (!reason) return;
    setServerError(null);
    try {
      const result = await requestExit({
        reason,
        destination_momo_number: momoNumber,
        momo_provider: provider,
      });
      setExitResult(result);
      setPhase('done');
    } catch (err: unknown) {
      console.error(err);
      const apiError = extractApiError(err);
      const code = apiError?.code;
      const message = apiError?.message;
      if (code === 'VAULT_EARLY_EXIT_ALREADY_PENDING') {
        setServerError(
          'An early-exit request is already in progress for this vault. ' +
            'Check your vault status.',
        );
      } else if (message) {
        setServerError(message);
      } else {
        setServerError('Something went wrong. Your request was not submitted — please try again.');
      }
    }
  }, [reason, momoNumber, provider, requestExit]);

  // ── Shared header ─────────────────────────────────────────────────────
  function renderHeader(title: string, onBack?: () => void) {
    return (
      <View style={styles.headerWrap}>
        <ScreenHeader title={title} onBack={onBack ?? (() => navigation.goBack())} />
      </View>
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // PHASE: reason
  // ═══════════════════════════════════════════════════════════════════
  if (phase === 'reason') {
    return (
      <SafeAreaView style={styles.safe}>
        {renderHeader('Early exit')}
        <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          {/* Explainer — honest, not shaming */}
          <View style={styles.explainerCard}>
            <Icon name="lock-open-outline" size={30} color={colors.gold.text} />
            <Text style={styles.explainerTitle}>Breaking this lock early</Text>
            <Text style={styles.explainerBody}>
              Life happens. You can exit this vault before your conditions are met. A{' '}
              <Text style={styles.explainerBold}>5% fee</Text> applies to your balance, and
              there&apos;s a <Text style={styles.explainerBold}>72-hour wait</Text> before the money
              is released — giving you time to change your mind.
            </Text>
          </View>

          {/* Vault context */}
          {vault && (
            <View style={styles.vaultContextRow}>
              <Text style={styles.vaultContextName}>{vault.name}</Text>
              <Text style={styles.vaultContextBalance}>
                GHS <Text>{vault.balance_cedis ?? '—'}</Text>
              </Text>
            </View>
          )}

          <Text style={styles.sectionHeading}>Why are you requesting this?</Text>
          <Text style={styles.sectionSub}>
            This is collected for product research only — it doesn&apos;t change your fee or the
            processing time.
          </Text>

          {/* Reason options */}
          {REASON_OPTIONS.map(opt => (
            <TouchableOpacity
              key={opt.id}
              style={[styles.reasonCard, reason === opt.id && styles.reasonCardSelected]}
              onPress={() => setReason(opt.id)}
              activeOpacity={0.82}
              accessibilityRole="radio"
              accessibilityState={{ selected: reason === opt.id }}
              accessibilityLabel={`${opt.label}. ${opt.description}`}
            >
              <Icon
                name={opt.icon}
                size={22}
                color={reason === opt.id ? colors.gold.text : colors.textSecondary}
              />
              <View style={styles.reasonBody}>
                <Text style={[styles.reasonLabel, reason === opt.id && styles.reasonLabelSelected]}>
                  {opt.label}
                </Text>
                <Text style={styles.reasonDesc}>{opt.description}</Text>
              </View>
              <View style={styles.radioOuter}>
                {reason === opt.id && <View style={styles.radioInner} />}
              </View>
            </TouchableOpacity>
          ))}

          <Text style={styles.fieldLabelMoMo}>Your MoMo number (for payout)</Text>

          <View style={styles.providerRow}>
            {PROVIDERS.map(p => (
              <TouchableOpacity
                key={p.id}
                style={[styles.providerPill, provider === p.id && PROVIDER_PILL_ACTIVE[p.id]]}
                onPress={() => setProvider(p.id)}
                accessibilityRole="radio"
                accessibilityState={{ selected: provider === p.id }}
                accessibilityLabel={p.label}
              >
                <Text style={[styles.providerText, provider === p.id && PROVIDER_TEXT_COLOR[p.id]]}>
                  {p.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>

          <TextInput
            style={styles.input}
            value={momoNumber}
            onChangeText={setMomoNumber}
            placeholder="0241234567"
            placeholderTextColor={colors.textTertiary}
            keyboardType="phone-pad"
            returnKeyType="done"
            accessibilityLabel="Your MoMo number for the early-exit payout"
          />
          {momoError && <Text style={styles.fieldError}>{momoError}</Text>}

          <PressableScale
            style={[styles.cta, !canProceedFromReason && styles.ctaDisabled]}
            disabled={!canProceedFromReason}
            onPress={() => setPhase('preview')}
            accessibilityRole="button"
            accessibilityLabel="See what you'll receive"
          >
            <Text style={styles.ctaText}>See what you&apos;ll receive</Text>
          </PressableScale>
        </ScrollView>
      </SafeAreaView>
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // PHASE: preview
  // ═══════════════════════════════════════════════════════════════════
  if (phase === 'preview') {
    const balancePesewas = vault?.balance_pesewas ?? 0;
    const penaltyPesewas = Math.floor(balancePesewas * 0.05);
    const releasePesewas = balancePesewas - penaltyPesewas;

    const estimatedRelease = new Date(Date.now() + 72 * 60 * 60 * 1000);

    return (
      <SafeAreaView style={styles.safe}>
        {renderHeader("What you'll receive", () => setPhase('reason'))}
        <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          {/* Release amount hero */}
          <View style={styles.releaseHero}>
            <Text style={styles.releaseLabel}>You&apos;ll receive</Text>
            <View style={styles.releaseAmountRow}>
              <Text style={styles.releaseCurrency}>GHS</Text>
              <Text style={styles.releaseAmount}>{formatCedis(releasePesewas)}</Text>
            </View>
            <Text style={styles.releaseWhen}>
              Released on {formatDatetime(estimatedRelease.toISOString())}
            </Text>
          </View>

          {/* Breakdown table */}
          <View style={styles.breakdownCard}>
            <BreakdownRow label="Current balance" value={`GHS ${formatCedis(balancePesewas)}`} />
            <BreakdownRow
              label="Early exit fee (5%)"
              value={`− GHS ${formatCedis(penaltyPesewas)}`}
              negative
            />
            <View style={styles.breakdownDivider} />
            <BreakdownRow
              label="You'll receive"
              value={`GHS ${formatCedis(releasePesewas)}`}
              bold
              positive
            />
          </View>

          {/* Cool-off explanation */}
          <View style={styles.coolOffBannerWrap}>
            <Banner
              tone="warning"
              title="72-hour cool-off"
              message={`Your money won't be released immediately. You can cancel at any time before ${formatDatetime(estimatedRelease.toISOString())} — your vault returns to normal with no fee charged.`}
            />
          </View>

          <Banner
            tone="info"
            icon="bulb-outline"
            message="Any deposits you make during the 72-hour window will be included in your release — the 5% fee only applies to your balance right now."
          />

          <PressableScale
            style={styles.cta}
            onPress={() => setPhase('confirm')}
            accessibilityRole="button"
          >
            <Text style={styles.ctaText}>Continue to confirmation</Text>
          </PressableScale>

          <TouchableOpacity
            style={styles.cancelLink}
            onPress={() => navigation.goBack()}
            accessibilityRole="button"
          >
            <Text style={styles.cancelLinkText}>Keep my lock — I&apos;ve changed my mind</Text>
          </TouchableOpacity>
        </ScrollView>
      </SafeAreaView>
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // PHASE: confirm
  // ═══════════════════════════════════════════════════════════════════
  if (phase === 'confirm') {
    const balancePesewas = vault?.balance_pesewas ?? 0;
    const penaltyPesewas = Math.floor(balancePesewas * 0.05);
    const releasePesewas = balancePesewas - penaltyPesewas;
    const reasonLabel = REASON_OPTIONS.find(r => r.id === reason)?.label ?? reason;

    return (
      <SafeAreaView style={styles.safe}>
        {renderHeader('Confirm early exit', () => setPhase('preview'))}
        <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          {/* Deliberate confirmation copy */}
          <Banner
            tone="error"
            title="You're starting the exit process"
            message="Once confirmed, the 72-hour cool-off begins. Your vault will show as Early Exit Pending. You can cancel at any time before the 72 hours are up."
          />

          {/* Final summary */}
          <View style={styles.finalSummary}>
            <FinalRow label="Vault" value={vault?.name ?? '—'} />
            <FinalRow label="Reason" value={reasonLabel ?? '—'} />
            <FinalRow label="Balance now" value={`GHS ${formatCedis(balancePesewas)}`} />
            <FinalRow label="Fee (5%)" value={`GHS ${formatCedis(penaltyPesewas)}`} negative />
            <FinalRow label="You'll receive" value={`GHS ${formatCedis(releasePesewas)}`} bold />
          </View>

          {serverError && <Banner tone="error" message={serverError} />}

          {/* Deliberate action button — not "Confirm" */}
          <PressableScale
            style={[styles.ctaDestructive, requesting && styles.ctaDisabled]}
            onPress={handleConfirm}
            disabled={requesting}
            accessibilityRole="button"
            accessibilityState={{ busy: requesting }}
            accessibilityLabel="I understand, start the 72-hour cool-off"
          >
            {requesting ? (
              <ActivityIndicator color={colors.neutral[0]} size="small" />
            ) : (
              <Text style={styles.ctaText}>I understand — start the 72-hour cool-off</Text>
            )}
          </PressableScale>

          <TouchableOpacity
            style={styles.cancelLink}
            onPress={() => navigation.goBack()}
            accessibilityRole="button"
          >
            <Text style={styles.cancelLinkText}>Keep my lock — I&apos;ve changed my mind</Text>
          </TouchableOpacity>
        </ScrollView>
      </SafeAreaView>
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // PHASE: done (cool-off started)
  // ═══════════════════════════════════════════════════════════════════
  if (phase === 'done' && exitResult) {
    return (
      <SafeAreaView style={styles.safe}>
        {renderHeader('Cool-off started', () => navigation.navigate('VaultDetail', { vaultId }))}
        <ScrollView
          contentContainerStyle={[styles.content, styles.doneContent]}
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.doneIconBadge}>
            <Icon name="hourglass-outline" size={36} color={colors.gold.text} />
          </View>
          <Text style={styles.doneTitle}>Your exit request is in</Text>
          <Text style={styles.doneCountdown}>
            {formatCountdown(exitResult.scheduled_release_at)}
          </Text>

          {/* Release summary */}
          <View style={[styles.breakdownCard, styles.fullWidth]}>
            <BreakdownRow
              label="Balance at request"
              value={`GHS ${exitResult.balance_at_request_cedis}`}
            />
            <BreakdownRow
              label="Fee (5%)"
              value={`GHS ${exitResult.penalty_amount_cedis}`}
              negative
            />
            <View style={styles.breakdownDivider} />
            <BreakdownRow
              label="Scheduled release"
              value={`GHS ${exitResult.release_amount_cedis}`}
              bold
              positive
            />
          </View>

          <Text style={styles.doneRelease}>
            Releasing on{'\n'}
            <Text style={styles.doneReleaseBold}>
              {formatDatetime(exitResult.scheduled_release_at)}
            </Text>
          </Text>

          <View style={styles.fullWidth}>
            <Banner
              tone="success"
              message="Your vault now shows as Early Exit Pending. You can still cancel from the vault screen before the deadline — no fee will be charged if you cancel."
            />
          </View>

          <PressableScale
            style={[styles.cta, styles.fullWidth]}
            onPress={() => navigation.navigate('VaultDetail', { vaultId })}
            accessibilityRole="button"
            accessibilityLabel="Back to vault"
          >
            <Text style={styles.ctaText}>Back to vault</Text>
          </PressableScale>
        </ScrollView>
      </SafeAreaView>
    );
  }

  return null;
}

// ─────────────────────────────────────────────────────────────────────────────
// Cancel Early Exit panel (embedded in VaultDetailScreen for EARLY_EXIT_PENDING)
// ─────────────────────────────────────────────────────────────────────────────

export function EarlyExitStatusPanel({
  vaultId,
  scheduledReleaseAt,
  releaseAmountCedis,
  penaltyAmountCedis,
}: {
  vaultId: string;
  scheduledReleaseAt: string;
  releaseAmountCedis: string;
  penaltyAmountCedis: string;
}) {
  const { mutateAsync: cancelExit, isPending } = useCancelEarlyExit(vaultId);
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const handleCancel = useCallback(async () => {
    Alert.alert('Cancel early exit?', 'Your vault will return to ACTIVE. No fee will be charged.', [
      { text: 'Keep exit request', style: 'cancel' },
      {
        text: 'Cancel exit',
        style: 'destructive',
        onPress: async () => {
          setCancelling(true);
          setCancelError(null);
          try {
            await cancelExit();
          } catch (err: unknown) {
            console.error(err);
            const code = extractApiError(err)?.code;
            if (code === 'VAULT_NO_PENDING_EARLY_EXIT') {
              setCancelError(
                'The exit request has already been processed and cannot be cancelled.',
              );
            } else {
              setCancelError('Could not cancel — please try again.');
            }
          } finally {
            setCancelling(false);
          }
        },
      },
    ]);
  }, [cancelExit]);

  return (
    <View style={panelStyles.container}>
      {/* Header row */}
      <View style={panelStyles.headerRow}>
        <Icon name="hourglass-outline" size={20} color={colors.status.warningText} />
        <View style={panelStyles.headerBody}>
          <Text style={panelStyles.headerTitle}>Early exit in progress</Text>
          <Text style={panelStyles.headerCountdown}>{formatCountdown(scheduledReleaseAt)}</Text>
        </View>
      </View>

      {/* Amount summary */}
      <View style={panelStyles.amountRow}>
        <View style={panelStyles.amountItem}>
          <Text style={panelStyles.amountLabel}>Fee</Text>
          <Text style={panelStyles.amountValue}>GHS {penaltyAmountCedis}</Text>
        </View>
        <View style={panelStyles.amountDivider} />
        <View style={panelStyles.amountItem}>
          <Text style={panelStyles.amountLabel}>You&apos;ll receive</Text>
          <Text style={[panelStyles.amountValue, panelStyles.amountPositive]}>
            GHS {releaseAmountCedis}
          </Text>
        </View>
      </View>

      <Text style={panelStyles.releaseDate}>Releases on {formatDatetime(scheduledReleaseAt)}</Text>

      {cancelError && <Text style={panelStyles.cancelError}>{cancelError}</Text>}

      {/* Cancel CTA */}
      <TouchableOpacity
        style={[panelStyles.cancelBtn, (isPending || cancelling) && panelStyles.cancelBtnDisabled]}
        onPress={handleCancel}
        disabled={isPending || cancelling}
        activeOpacity={0.85}
        accessibilityRole="button"
        accessibilityLabel="Cancel early exit request"
      >
        {isPending || cancelling ? (
          <ActivityIndicator size="small" color={colors.status.error} />
        ) : (
          <Text style={panelStyles.cancelBtnText}>Cancel early exit</Text>
        )}
      </TouchableOpacity>

      <Text style={panelStyles.cancelNote}>Cancel before the deadline — no fee applies.</Text>
    </View>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

function BreakdownRow({
  label,
  value,
  negative,
  positive,
  bold,
}: {
  label: string;
  value: string;
  negative?: boolean;
  positive?: boolean;
  bold?: boolean;
}) {
  return (
    <View style={bdStyles.row}>
      <Text style={[bdStyles.label, bold && bdStyles.labelBold]}>{label}</Text>
      <Text
        style={[
          bdStyles.value,
          negative && bdStyles.negative,
          positive && bdStyles.positive,
          bold && bdStyles.valueBold,
        ]}
      >
        {value}
      </Text>
    </View>
  );
}

function FinalRow({
  label,
  value,
  negative,
  bold,
}: {
  label: string;
  value: string;
  negative?: boolean;
  bold?: boolean;
}) {
  return (
    <View style={finalStyles.row}>
      <Text style={finalStyles.label}>{label}</Text>
      <Text style={[finalStyles.value, negative && finalStyles.negative, bold && finalStyles.bold]}>
        {value}
      </Text>
    </View>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Styles
// ─────────────────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  content: { paddingHorizontal: spacing.lg, paddingTop: spacing.lg, paddingBottom: spacing['5xl'] },
  doneContent: { alignItems: 'center', paddingTop: spacing['2xl'] },
  fullWidth: { width: '100%' },

  headerWrap: {
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },

  // Reason phase
  explainerCard: {
    backgroundColor: colors.gold.light,
    borderRadius: radii.lg,
    padding: spacing.xl,
    marginBottom: spacing.lg,
    alignItems: 'center',
  },
  explainerTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: colors.textPrimary,
    marginTop: spacing.sm,
    marginBottom: spacing.sm,
    textAlign: 'center',
  },
  explainerBody: { fontSize: 13, color: colors.textSecondary, lineHeight: 19, textAlign: 'center' },
  explainerBold: { fontWeight: '700', color: colors.textPrimary },

  vaultContextRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    padding: spacing.md,
    marginBottom: spacing.xl,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
  },
  vaultContextName: { fontSize: 14, fontWeight: '700', color: colors.textPrimary },
  vaultContextBalance: { fontSize: 14, fontWeight: '600', color: colors.gold.text },

  sectionHeading: {
    fontSize: 14,
    fontWeight: '700',
    color: colors.textPrimary,
    marginBottom: spacing.xs,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  sectionSub: {
    fontSize: 12,
    color: colors.textSecondary,
    marginBottom: spacing.lg,
    lineHeight: 17,
  },

  reasonCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    padding: spacing.lg,
    marginBottom: spacing.sm,
    borderWidth: 2,
    borderColor: colors.border,
  },
  reasonCardSelected: { borderColor: colors.gold.base, backgroundColor: colors.gold.light },
  reasonBody: { flex: 1 },
  reasonLabel: { fontSize: 14, fontWeight: '700', color: colors.textPrimary, marginBottom: 2 },
  reasonLabelSelected: { color: colors.gold.text },
  reasonDesc: { fontSize: 12, color: colors.textSecondary },
  radioOuter: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 2,
    borderColor: colors.gold.base,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioInner: { width: 11, height: 11, borderRadius: 6, backgroundColor: colors.gold.base },

  fieldLabelMoMo: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginTop: spacing.sm,
  },
  providerRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    marginBottom: spacing.md,
  },
  providerPill: {
    borderWidth: 1.5,
    borderColor: colors.borderStrong,
    borderRadius: radii.md,
    paddingHorizontal: spacing.md,
    paddingVertical: 7,
  },
  providerText: { fontSize: 12, fontWeight: '600', color: colors.textSecondary },
  input: {
    backgroundColor: colors.surface,
    borderWidth: 1.5,
    borderColor: colors.borderStrong,
    borderRadius: radii.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    fontSize: 15,
    color: colors.textPrimary,
    marginBottom: spacing.lg,
  },
  fieldError: {
    fontSize: 12,
    color: colors.status.error,
    marginTop: -spacing.md,
    marginBottom: spacing.lg,
  },

  // Preview phase
  releaseHero: { alignItems: 'center', paddingVertical: spacing['2xl'] },
  releaseLabel: {
    fontSize: 13,
    color: colors.textSecondary,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: spacing.sm,
  },
  releaseAmountRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: spacing.xs,
    marginBottom: spacing.sm,
  },
  releaseCurrency: { fontSize: 20, fontWeight: '700', color: colors.status.successText },
  releaseAmount: {
    fontSize: 44,
    fontWeight: '800',
    color: colors.status.successText,
    letterSpacing: -1.5,
  },
  releaseWhen: { fontSize: 13, color: colors.textSecondary },

  breakdownCard: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    marginBottom: spacing.lg,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
  },
  breakdownDivider: { height: 1, backgroundColor: colors.neutral[100], marginVertical: spacing.xs },

  coolOffBannerWrap: { width: '100%' },

  cancelLink: { alignItems: 'center', marginTop: spacing.md },
  cancelLinkText: { fontSize: 14, color: colors.textSecondary, textDecorationLine: 'underline' },

  // Confirm phase
  finalSummary: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    marginBottom: spacing.xl,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
  },

  // Done phase
  doneIconBadge: {
    width: 84,
    height: 84,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.lg,
  },
  doneTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: colors.textPrimary,
    marginBottom: spacing.xs,
  },
  doneCountdown: {
    fontSize: 28,
    fontWeight: '800',
    color: colors.gold.text,
    letterSpacing: -0.8,
    marginBottom: spacing['2xl'],
  },
  doneRelease: {
    fontSize: 13,
    color: colors.textSecondary,
    textAlign: 'center',
    marginVertical: spacing.lg,
  },
  doneReleaseBold: { fontWeight: '700', color: colors.textPrimary },

  // Shared CTA
  cta: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingVertical: 15,
    alignItems: 'center',
  },
  ctaDestructive: {
    backgroundColor: colors.status.error,
    borderRadius: radii.md,
    paddingVertical: 15,
    alignItems: 'center',
  },
  ctaDisabled: { backgroundColor: colors.neutral[300] },
  ctaText: {
    fontSize: 15,
    fontWeight: '700',
    color: colors.neutral[900],
    textAlign: 'center',
    lineHeight: 20,
  },
});

const bdStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
  },
  label: { fontSize: 13, color: colors.textSecondary },
  labelBold: { fontWeight: '700', color: colors.textPrimary },
  value: { fontSize: 13, fontWeight: '600', color: colors.textPrimary },
  valueBold: { fontSize: 15, fontWeight: '800' },
  negative: { color: colors.status.error },
  positive: { color: colors.status.successText },
});

const finalStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.neutral[100],
  },
  label: { fontSize: 13, color: colors.textSecondary },
  value: { fontSize: 14, fontWeight: '600', color: colors.textPrimary },
  negative: { color: colors.status.error },
  bold: { fontSize: 15, fontWeight: '800', color: colors.status.successText },
});

const panelStyles = StyleSheet.create({
  container: {
    backgroundColor: colors.status.warningBg,
    borderRadius: radii.lg,
    padding: spacing.xl,
    margin: spacing.lg,
    borderWidth: 1,
    borderColor: colors.status.warningBorder,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.sm,
    marginBottom: spacing.lg,
  },
  headerBody: { flex: 1 },
  headerTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: colors.status.warningText,
    marginBottom: 2,
  },
  headerCountdown: {
    fontSize: 22,
    fontWeight: '800',
    color: colors.status.warningText,
    letterSpacing: -0.5,
  },

  amountRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255,255,255,0.6)',
    borderRadius: radii.md,
    padding: spacing.md,
    marginBottom: spacing.sm,
  },
  amountItem: { flex: 1, alignItems: 'center' },
  amountDivider: { width: 1, height: 36, backgroundColor: colors.status.warningBorder },
  amountLabel: {
    fontSize: 11,
    color: colors.status.warningInkSoft,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: spacing.xs,
  },
  amountValue: { fontSize: 16, fontWeight: '800', color: colors.status.warningInk },
  amountPositive: { color: colors.status.successText },

  releaseDate: {
    fontSize: 12,
    color: colors.status.warningInkSoft,
    marginBottom: spacing.md,
    textAlign: 'center',
  },

  cancelBtn: {
    borderWidth: 1.5,
    borderColor: colors.status.error,
    borderRadius: radii.md,
    paddingVertical: 13,
    alignItems: 'center',
    backgroundColor: colors.status.errorBg,
    marginBottom: spacing.sm,
  },
  cancelBtnDisabled: { opacity: 0.5 },
  cancelBtnText: { fontSize: 14, fontWeight: '700', color: colors.status.error },

  cancelNote: { fontSize: 12, color: colors.status.warningInkSoft, textAlign: 'center' },
  cancelError: {
    fontSize: 12,
    color: colors.status.error,
    marginBottom: spacing.sm,
    textAlign: 'center',
  },
});
