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

// ── Types ──────────────────────────────────────────────────────────────────

type Nav = NativeStackNavigationProp<RootStackParamList, 'EarlyExit'>;
type Route = RouteProp<RootStackParamList, 'EarlyExit'>;
type Phase = 'reason' | 'preview' | 'confirm' | 'done';

// ── MoMo provider metadata (same set as DepositScreen/WithdrawScreen) ──────

const PROVIDERS = [
  { id: 'mtn', label: 'MTN MoMo', color: '#FBB01C' },
  { id: 'vodafone', label: 'Vodafone Cash', color: '#E10A0A' },
  { id: 'airteltigo', label: 'AirtelTigo', color: '#FF6200' },
] as const;

type ProviderId = (typeof PROVIDERS)[number]['id'];

// Plain objects — accessed dynamically, so StyleSheet.create would flag them
// as unused; object literals in JSX style props would trigger no-inline-styles.
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
  icon: string;
}> = [
  {
    id: 'SCHOOL_FEES_EMERGENCY',
    label: 'School fees',
    description: 'Tuition or school-related payment due',
    icon: '📚',
  },
  {
    id: 'MEDICAL',
    label: 'Medical emergency',
    description: 'Healthcare costs or an urgent medical need',
    icon: '🏥',
  },
  {
    id: 'FAMILY',
    label: 'Family need',
    description: 'An unexpected family obligation or expense',
    icon: '👨‍👩‍👧',
  },
  {
    id: 'OTHER',
    label: 'Something else',
    description: 'Another reason not listed above',
    icon: '✳️',
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

  const canProceedFromReason = !!reason && momoNumber.trim().length > 0;

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
      <View style={styles.header}>
        <TouchableOpacity
          style={styles.headerBtn}
          onPress={onBack ?? (() => navigation.goBack())}
          accessibilityLabel="Go back"
        >
          <Text style={styles.headerBtnIcon}>←</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle} numberOfLines={1}>
          {title}
        </Text>
        <View style={styles.headerBtn} />
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
            <Text style={styles.explainerIcon}>🔓</Text>
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
              <Text style={styles.reasonIcon}>{opt.icon}</Text>
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
            keyboardType="phone-pad"
            returnKeyType="done"
            accessibilityLabel="Your MoMo number for the early-exit payout"
          />

          <TouchableOpacity
            style={[styles.cta, !canProceedFromReason && styles.ctaDisabled]}
            disabled={!canProceedFromReason}
            onPress={() => setPhase('preview')}
            activeOpacity={0.85}
            accessibilityRole="button"
            accessibilityLabel="See what you'll receive"
          >
            <Text style={styles.ctaText}>See what you&apos;ll receive</Text>
          </TouchableOpacity>
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
          <View style={styles.coolOffCard}>
            <Text style={styles.coolOffTitle}>
              ⏳{'  '}
              <Text>72-hour cool-off</Text>
            </Text>
            <Text style={styles.coolOffBody}>
              Your money won&apos;t be released immediately. You can cancel at any time before{' '}
              <Text style={styles.coolOffBold}>
                {formatDatetime(estimatedRelease.toISOString())}
              </Text>{' '}
              — your vault returns to normal with no fee charged.
            </Text>
          </View>

          {/* Deposit note */}
          <View style={styles.depositNote}>
            <Text style={styles.depositNoteText}>
              💡 Any deposits you make{' '}
              <Text style={styles.depositNoteBold}>during the 72-hour window</Text> will be included
              in your release — the 5% fee only applies to your balance right now.
            </Text>
          </View>

          <TouchableOpacity
            style={styles.cta}
            onPress={() => setPhase('confirm')}
            activeOpacity={0.85}
            accessibilityRole="button"
          >
            <Text style={styles.ctaText}>Continue to confirmation</Text>
          </TouchableOpacity>

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
          <View style={styles.confirmIntro}>
            <Text style={styles.confirmIntroIcon}>⚠️</Text>
            <Text style={styles.confirmIntroTitle}>You&apos;re starting the exit process</Text>
            <Text style={styles.confirmIntroBody}>
              Once confirmed, the 72-hour cool-off begins. Your vault will show as{' '}
              <Text style={styles.confirmIntroBold}>Early Exit Pending</Text>. You can cancel at any
              time before the 72 hours are up.
            </Text>
          </View>

          {/* Final summary */}
          <View style={styles.finalSummary}>
            <FinalRow label="Vault" value={vault?.name ?? '—'} />
            <FinalRow label="Reason" value={reasonLabel ?? '—'} />
            <FinalRow label="Balance now" value={`GHS ${formatCedis(balancePesewas)}`} />
            <FinalRow label="Fee (5%)" value={`GHS ${formatCedis(penaltyPesewas)}`} negative />
            <FinalRow label="You'll receive" value={`GHS ${formatCedis(releasePesewas)}`} bold />
          </View>

          {serverError && (
            <View style={styles.serverErrorBox}>
              <Text style={styles.serverErrorText}>{serverError}</Text>
            </View>
          )}

          {/* Deliberate action button — not "Confirm" */}
          <TouchableOpacity
            style={[styles.ctaDestructive, requesting && styles.ctaDisabled]}
            onPress={handleConfirm}
            disabled={requesting}
            activeOpacity={0.85}
            accessibilityRole="button"
            accessibilityState={{ busy: requesting }}
            accessibilityLabel="I understand, start the 72-hour cool-off"
          >
            {requesting ? (
              <ActivityIndicator color="#FFFFFF" size="small" />
            ) : (
              <Text style={styles.ctaText}>I understand — start the 72-hour cool-off</Text>
            )}
          </TouchableOpacity>

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
          <Text style={styles.doneIcon}>⏳</Text>
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

          <View style={styles.doneNote}>
            <Text style={styles.doneNoteText}>
              Your vault now shows as <Text style={styles.doneNoteBold}>Early Exit Pending</Text>.
              You can still cancel from the vault screen before the deadline — no fee will be
              charged if you cancel.
            </Text>
          </View>

          <TouchableOpacity
            style={[styles.cta, styles.fullWidth]}
            onPress={() => navigation.navigate('VaultDetail', { vaultId })}
            accessibilityRole="button"
            accessibilityLabel="Back to vault"
          >
            <Text style={styles.ctaText}>Back to vault</Text>
          </TouchableOpacity>
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
        <Text style={panelStyles.headerIcon}>⏳</Text>
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
          <ActivityIndicator size="small" color="#DC2626" />
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

const INDIGO = '#4F46E5';
const DARK = '#1A1A2E';
const MUTED = '#6B7280';
const BACKGROUND = '#F8F9FF';
const GREEN = '#059669';
const AMBER = '#D97706';
const RED = '#DC2626';

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: BACKGROUND },
  content: { paddingHorizontal: 16, paddingTop: 16, paddingBottom: 48 },
  doneContent: { alignItems: 'center', paddingTop: 32 },
  fullWidth: { width: '100%' },

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
  headerBtn: { width: 40, height: 40, justifyContent: 'center' },
  headerBtnIcon: { fontSize: 22, color: DARK },
  headerTitle: { fontSize: 17, fontWeight: '700', color: DARK, flex: 1, textAlign: 'center' },

  // Reason phase
  explainerCard: {
    backgroundColor: '#F0F4FF',
    borderRadius: 16,
    padding: 18,
    marginBottom: 16,
    alignItems: 'center',
  },
  explainerIcon: { fontSize: 32, marginBottom: 10 },
  explainerTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: DARK,
    marginBottom: 6,
    textAlign: 'center',
  },
  explainerBody: { fontSize: 13, color: MUTED, lineHeight: 19, textAlign: 'center' },
  explainerBold: { fontWeight: '700', color: DARK },

  vaultContextRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 14,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#EDEDF0',
  },
  vaultContextName: { fontSize: 14, fontWeight: '700', color: DARK },
  vaultContextBalance: { fontSize: 14, fontWeight: '600', color: INDIGO },

  sectionHeading: {
    fontSize: 14,
    fontWeight: '700',
    color: DARK,
    marginBottom: 4,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  sectionSub: { fontSize: 12, color: MUTED, marginBottom: 16, lineHeight: 17 },

  reasonCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: '#FFFFFF',
    borderRadius: 14,
    padding: 16,
    marginBottom: 10,
    borderWidth: 2,
    borderColor: '#EDEDF0',
  },
  reasonCardSelected: { borderColor: INDIGO, backgroundColor: '#F5F3FF' },
  reasonIcon: { fontSize: 24 },
  reasonBody: { flex: 1 },
  reasonLabel: { fontSize: 14, fontWeight: '700', color: DARK, marginBottom: 2 },
  reasonLabelSelected: { color: INDIGO },
  reasonDesc: { fontSize: 12, color: MUTED },
  radioOuter: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 2,
    borderColor: INDIGO,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioInner: { width: 11, height: 11, borderRadius: 6, backgroundColor: INDIGO },

  fieldLabelMoMo: {
    fontSize: 12,
    fontWeight: '700',
    color: DARK,
    marginBottom: 8,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginTop: 8,
  },
  providerRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 12 },
  providerPill: {
    borderWidth: 1.5,
    borderColor: '#D1D5DB',
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 7,
  },
  providerText: { fontSize: 12, fontWeight: '600', color: MUTED },
  input: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1.5,
    borderColor: '#D1D5DB',
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 15,
    color: DARK,
    marginBottom: 16,
  },

  // Preview phase
  releaseHero: { alignItems: 'center', paddingVertical: 28 },
  releaseLabel: {
    fontSize: 13,
    color: MUTED,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: 8,
  },
  releaseAmountRow: { flexDirection: 'row', alignItems: 'baseline', gap: 6, marginBottom: 8 },
  releaseCurrency: { fontSize: 20, fontWeight: '700', color: GREEN },
  releaseAmount: { fontSize: 44, fontWeight: '800', color: GREEN, letterSpacing: -1.5 },
  releaseWhen: { fontSize: 13, color: MUTED },

  breakdownCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 4,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#EDEDF0',
    overflow: 'hidden',
  },
  breakdownDivider: { height: 1, backgroundColor: '#F3F4F6', marginVertical: 4 },

  coolOffCard: {
    backgroundColor: '#FEF3C7',
    borderRadius: 14,
    padding: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#FDE68A',
  },
  coolOffTitle: { fontSize: 14, fontWeight: '700', color: AMBER, marginBottom: 6 },
  coolOffBody: { fontSize: 13, color: '#78350F', lineHeight: 19 },
  coolOffBold: { fontWeight: '700' },

  depositNote: {
    backgroundColor: '#EEF2FF',
    borderRadius: 12,
    padding: 14,
    marginBottom: 24,
  },
  depositNoteText: { fontSize: 13, color: '#3730A3', lineHeight: 18 },
  depositNoteBold: { fontWeight: '700' },

  cancelLink: { alignItems: 'center', marginTop: 16 },
  cancelLinkText: { fontSize: 14, color: MUTED, textDecorationLine: 'underline' },

  // Confirm phase
  confirmIntro: {
    backgroundColor: '#FEF2F2',
    borderRadius: 16,
    padding: 18,
    marginBottom: 20,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#FCA5A5',
  },
  confirmIntroIcon: { fontSize: 28, marginBottom: 8 },
  confirmIntroTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: DARK,
    marginBottom: 6,
    textAlign: 'center',
  },
  confirmIntroBody: { fontSize: 13, color: MUTED, lineHeight: 19, textAlign: 'center' },
  confirmIntroBold: { fontWeight: '700', color: DARK },

  finalSummary: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#EDEDF0',
    overflow: 'hidden',
  },
  serverErrorBox: {
    backgroundColor: '#FEF2F2',
    borderRadius: 12,
    padding: 14,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#FCA5A5',
  },
  serverErrorText: { fontSize: 13, color: '#991B1B' },

  // Done phase
  doneIcon: { fontSize: 64, marginBottom: 16 },
  doneTitle: { fontSize: 20, fontWeight: '700', color: DARK, marginBottom: 4 },
  doneCountdown: {
    fontSize: 28,
    fontWeight: '800',
    color: INDIGO,
    letterSpacing: -0.8,
    marginBottom: 24,
  },
  doneRelease: { fontSize: 13, color: MUTED, textAlign: 'center', marginVertical: 16 },
  doneReleaseBold: { fontWeight: '700', color: DARK },
  doneNote: {
    backgroundColor: '#F0FDF4',
    borderRadius: 12,
    padding: 14,
    marginBottom: 24,
    width: '100%',
  },
  doneNoteText: { fontSize: 13, color: '#065F46', lineHeight: 18 },
  doneNoteBold: { fontWeight: '700' },

  // Shared CTA
  cta: {
    backgroundColor: INDIGO,
    borderRadius: 14,
    paddingVertical: 15,
    alignItems: 'center',
    shadowColor: INDIGO,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.28,
    shadowRadius: 10,
    elevation: 4,
  },
  ctaDestructive: {
    backgroundColor: RED,
    borderRadius: 14,
    paddingVertical: 15,
    alignItems: 'center',
    shadowColor: RED,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.22,
    shadowRadius: 10,
    elevation: 4,
  },
  ctaDisabled: { backgroundColor: '#A5B4FC', shadowOpacity: 0, elevation: 0 },
  ctaText: {
    fontSize: 15,
    fontWeight: '700',
    color: '#FFFFFF',
    textAlign: 'center',
    lineHeight: 20,
  },
});

const bdStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  label: { fontSize: 13, color: MUTED },
  labelBold: { fontWeight: '700', color: DARK },
  value: { fontSize: 13, fontWeight: '600', color: DARK },
  valueBold: { fontSize: 15, fontWeight: '800' },
  negative: { color: RED },
  positive: { color: GREEN },
});

const finalStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
  },
  label: { fontSize: 13, color: MUTED },
  value: { fontSize: 14, fontWeight: '600', color: DARK },
  negative: { color: RED },
  bold: { fontSize: 15, fontWeight: '800', color: GREEN },
});

const panelStyles = StyleSheet.create({
  container: {
    backgroundColor: '#FFFBEB',
    borderRadius: 16,
    padding: 18,
    margin: 16,
    borderWidth: 1,
    borderColor: '#FDE68A',
  },
  headerRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 10, marginBottom: 16 },
  headerIcon: { fontSize: 24 },
  headerBody: { flex: 1 },
  headerTitle: { fontSize: 15, fontWeight: '700', color: '#92400E', marginBottom: 2 },
  headerCountdown: { fontSize: 22, fontWeight: '800', color: AMBER, letterSpacing: -0.5 },

  amountRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255,255,255,0.6)',
    borderRadius: 12,
    padding: 14,
    marginBottom: 10,
  },
  amountItem: { flex: 1, alignItems: 'center' },
  amountDivider: { width: 1, height: 36, backgroundColor: '#FDE68A' },
  amountLabel: {
    fontSize: 11,
    color: '#B45309',
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: 4,
  },
  amountValue: { fontSize: 16, fontWeight: '800', color: '#78350F' },
  amountPositive: { color: GREEN },

  releaseDate: { fontSize: 12, color: '#B45309', marginBottom: 14, textAlign: 'center' },

  cancelBtn: {
    borderWidth: 1.5,
    borderColor: RED,
    borderRadius: 12,
    paddingVertical: 13,
    alignItems: 'center',
    backgroundColor: '#FEF2F2',
    marginBottom: 8,
  },
  cancelBtnDisabled: { opacity: 0.5 },
  cancelBtnText: { fontSize: 14, fontWeight: '700', color: RED },

  cancelNote: { fontSize: 12, color: '#B45309', textAlign: 'center' },
  cancelError: { fontSize: 12, color: RED, marginBottom: 8, textAlign: 'center' },
});
