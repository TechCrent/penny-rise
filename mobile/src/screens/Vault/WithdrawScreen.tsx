import React, { useRef, useState, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import axios from 'axios';
import { LinearGradient } from 'expo-linear-gradient';
import { extractApiError } from '../../api/client';
import { RootStackParamList } from '../../navigation/RootNavigator';
import { useVaultDetail } from '../../api/hooks/useVaultDetail';
import { useAuth } from '../../hooks/useAuth';
import { useVaultWithdrawal } from '../../api/hooks/useVaultWithdrawal';
import { PROVIDERS, ProviderId, validateMomoNumber } from '../../constants/momoProviders';
import { Banner, Icon, PressableScale, ScreenHeader } from '../../components/ui';
import { colors, radii, spacing } from '../../theme';

// ── Types ──────────────────────────────────────────────────────────────────

type Nav = NativeStackNavigationProp<RootStackParamList, 'Withdraw'>;
type Route = RouteProp<RootStackParamList, 'Withdraw'>;

type Phase = 'amount' | 'confirm' | 'pending' | 'completed';

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

// ── Helpers ────────────────────────────────────────────────────────────────

function generateKey(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

function formatCedis(pesewas: number): string {
  return (pesewas / 100).toLocaleString('en-GH', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function ghsToPesewas(ghs: string): number {
  const parsed = parseFloat(ghs || '0');
  return isNaN(parsed) ? 0 : Math.round(parsed * 100);
}

// ─────────────────────────────────────────────────────────────────────────────

export default function WithdrawScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const { user } = useAuth();
  const { vaultId } = route.params;

  const { data: vault } = useVaultDetail(vaultId);
  const { mutateAsync, isPending } = useVaultWithdrawal(vaultId);

  const idempotencyKeyRef = useRef<string>(generateKey());

  const [phase, setPhase] = useState<Phase>('amount');
  const [amountGhs, setAmountGhs] = useState('');
  const [momoNumber, setMomoNumber] = useState(user?.momoNumber ?? '');
  const [provider, setProvider] = useState<ProviderId>('mtn');
  const [amountError, setAmountError] = useState<string | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);
  const [txnRef, setTxnRef] = useState<string | null>(null);

  const amountPesewas = ghsToPesewas(amountGhs);
  const availablePesewas = vault?.balance_pesewas ?? 0;
  const afterPesewas = Math.max(0, availablePesewas - amountPesewas);

  function validateAmount(): boolean {
    const ghs = parseFloat(amountGhs || '0');
    if (isNaN(ghs) || ghs <= 0) {
      setAmountError('Enter an amount greater than 0.');
      return false;
    }
    if (amountPesewas > availablePesewas) {
      setAmountError(`Amount exceeds your balance of GHS ${formatCedis(availablePesewas)}.`);
      return false;
    }
    const momoError = validateMomoNumber(provider, momoNumber);
    if (momoError) {
      setAmountError(momoError);
      return false;
    }
    setAmountError(null);
    return true;
  }

  const handleConfirm = useCallback(async () => {
    setServerError(null);
    try {
      const resp = await mutateAsync({
        payload: {
          amount: amountPesewas,
          destination_momo_number: momoNumber,
          momo_provider: provider,
        },
        idempotencyKey: idempotencyKeyRef.current,
      });
      setTxnRef(resp.transaction_reference);
      setPhase(resp.status === 'COMPLETED' ? 'completed' : 'pending');
    } catch (err: unknown) {
      console.error(err);
      const apiError = extractApiError(err);
      const status = axios.isAxiosError(err) ? err.response?.status : undefined;
      const code = apiError?.code;
      const message = apiError?.message;

      if (code === 'VAULT_INSUFFICIENT_BALANCE') {
        setServerError(
          'Your vault balance is too low for this withdrawal. ' +
            'Refresh to see your latest balance and try a smaller amount.',
        );
      } else if (code === 'VAULT_WITHDRAWAL_NOT_PERMITTED') {
        setServerError(
          'LOCKED vaults cannot withdraw via this screen. ' + 'Use the early-exit flow instead.',
        );
      } else if (status === 403) {
        setServerError("You don't have permission to withdraw from this vault.");
      } else if (message) {
        setServerError(message);
      } else {
        setServerError('Something went wrong. No money was moved — please try again.');
      }
    }
  }, [amountPesewas, momoNumber, provider, mutateAsync]);

  function renderHeader(title: string, backFn?: () => void) {
    return (
      <View style={styles.headerWrap}>
        <ScreenHeader title={title} onBack={backFn ?? (() => navigation.goBack())} />
      </View>
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // PHASE: amount
  // ═══════════════════════════════════════════════════════════════════
  if (phase === 'amount') {
    const exceedsBalance = amountPesewas > availablePesewas && amountPesewas > 0;

    return (
      <SafeAreaView style={styles.safe}>
        <KeyboardAvoidingView
          style={styles.flex}
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        >
          {renderHeader('Withdraw')}

          <ScrollView
            contentContainerStyle={styles.content}
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
          >
            <LinearGradient
              colors={[colors.heroFrom, colors.heroTo]}
              start={{ x: 0, y: 0 }}
              end={{ x: 1, y: 1 }}
              style={styles.balanceCard}
            >
              <Text style={styles.balanceLabel}>Available to withdraw</Text>
              <Text style={styles.balanceValue}>
                GHS {vault ? formatCedis(availablePesewas) : '—'}
              </Text>
              <Text style={styles.balanceSub}>{vault?.name}</Text>
            </LinearGradient>

            <Text style={styles.fieldLabel}>Amount (GHS)</Text>
            <View style={styles.amountRow}>
              <Text style={styles.ghsPrefix}>GHS</Text>
              <TextInput
                style={[styles.amountInput, exceedsBalance && styles.amountInputError]}
                value={amountGhs}
                onChangeText={t => {
                  setAmountGhs(t.replace(/[^0-9.]/g, ''));
                  setAmountError(null);
                }}
                keyboardType="decimal-pad"
                placeholder="0.00"
                placeholderTextColor={colors.neutral[300]}
                returnKeyType="done"
                autoFocus
                accessibilityLabel="Withdrawal amount in Ghana cedis"
              />
            </View>

            {amountPesewas > 0 && !exceedsBalance && (
              <Text style={styles.afterBalance}>
                After withdrawal: GHS {formatCedis(afterPesewas)}
              </Text>
            )}

            {exceedsBalance && (
              <Banner
                tone="error"
                message={`Amount exceeds your balance of GHS ${formatCedis(availablePesewas)}`}
              />
            )}

            {amountError && !exceedsBalance && <Text style={styles.fieldError}>{amountError}</Text>}

            <Text style={styles.fieldLabelDest}>Destination MoMo number</Text>

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
                  <Text
                    style={[styles.providerText, provider === p.id && PROVIDER_TEXT_COLOR[p.id]]}
                  >
                    {p.label}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            <TextInput
              style={styles.input}
              value={momoNumber}
              onChangeText={setMomoNumber}
              keyboardType="phone-pad"
              placeholder="024 000 0000"
              placeholderTextColor={colors.textTertiary}
              returnKeyType="done"
              accessibilityLabel="MoMo destination number"
            />

            <Banner
              tone="warning"
              message="MoMo transfers usually arrive within 5–10 minutes. Occasionally up to 24 hours during network congestion."
            />

            <PressableScale
              style={[styles.cta, exceedsBalance && styles.ctaDisabled]}
              disabled={exceedsBalance}
              onPress={() => {
                if (validateAmount()) setPhase('confirm');
              }}
              accessibilityRole="button"
              accessibilityLabel="Review withdrawal"
            >
              <Text style={styles.ctaText}>Review withdrawal</Text>
            </PressableScale>
          </ScrollView>
        </KeyboardAvoidingView>
      </SafeAreaView>
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // PHASE: confirm
  // ═══════════════════════════════════════════════════════════════════
  if (phase === 'confirm') {
    const providerLabel = PROVIDERS.find(p => p.id === provider)?.label ?? provider;

    return (
      <SafeAreaView style={styles.safe}>
        {renderHeader('Confirm withdrawal', () => setPhase('amount'))}

        <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          <LinearGradient
            colors={[colors.heroFrom, colors.heroTo]}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 1 }}
            style={styles.summaryCard}
          >
            <Text style={styles.summaryHeading}>You&apos;re withdrawing</Text>
            <Text style={styles.summaryAmount}>GHS {formatCedis(amountPesewas)}</Text>
            <View style={styles.summaryDivider} />
            <SummaryRow label="From" value={vault?.name ?? '—'} />
            <SummaryRow label="To" value={`${providerLabel} · ${momoNumber}`} />
            <SummaryRow label="After balance" value={`GHS ${formatCedis(afterPesewas)}`} />
            <SummaryRow label="Fee" value="Free" />
          </LinearGradient>

          <Banner
            tone="warning"
            title="Takes a few minutes"
            message={`Your money will arrive on ${momoNumber} within 5–10 minutes. We'll notify you when it lands.`}
          />

          {serverError && <Banner tone="error" message={serverError} />}

          <PressableScale
            style={[styles.cta, isPending && styles.ctaDisabled]}
            onPress={handleConfirm}
            disabled={isPending}
            accessibilityRole="button"
            accessibilityState={{ busy: isPending }}
            accessibilityLabel={`Confirm withdrawal of GHS ${formatCedis(amountPesewas)}`}
          >
            {isPending ? (
              <ActivityIndicator color={colors.neutral[900]} size="small" />
            ) : (
              <Text style={styles.ctaText}>Withdraw GHS {formatCedis(amountPesewas)}</Text>
            )}
          </PressableScale>
        </ScrollView>
      </SafeAreaView>
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // PHASE: completed
  // ═══════════════════════════════════════════════════════════════════
  if (phase === 'completed') {
    return (
      <SafeAreaView style={styles.safe}>
        {renderHeader('Withdrawal complete', () => navigation.navigate('VaultDetail', { vaultId }))}

        <ScrollView
          contentContainerStyle={[styles.content, styles.pendingContent]}
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.pendingHero}>
            <View style={styles.successIconBadge}>
              <Icon name="checkmark-circle" size={40} color={colors.status.success} />
            </View>
            <Text style={styles.pendingTitle}>Money sent</Text>
            <Text style={styles.pendingAmount}>GHS {formatCedis(amountPesewas)}</Text>
            <Text style={styles.pendingDestination}>
              → {PROVIDERS.find(p => p.id === provider)?.label} · {momoNumber}
            </Text>
          </View>

          {txnRef && (
            <View style={styles.refCard}>
              <Text style={styles.refLabel}>Reference</Text>
              <Text style={styles.refValue} selectable>
                {txnRef}
              </Text>
            </View>
          )}

          <PressableScale
            style={styles.cta}
            onPress={() => navigation.navigate('VaultDetail', { vaultId })}
            accessibilityRole="button"
            accessibilityLabel="Back to vault"
          >
            <Text style={styles.ctaText}>Back to vault</Text>
          </PressableScale>

          <TouchableOpacity
            style={styles.homeLink}
            onPress={() => navigation.navigate('Main', { screen: 'Home' })}
            accessibilityRole="button"
          >
            <Text style={styles.homeLinkText}>Go to home screen</Text>
          </TouchableOpacity>
        </ScrollView>
      </SafeAreaView>
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // PHASE: pending
  // ═══════════════════════════════════════════════════════════════════
  return (
    <SafeAreaView style={styles.safe}>
      {renderHeader('Withdrawal submitted', () => navigation.navigate('VaultDetail', { vaultId }))}

      <ScrollView
        contentContainerStyle={[styles.content, styles.pendingContent]}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.pendingHero}>
          <View style={styles.pendingIconBadge}>
            <Icon name="rocket-outline" size={36} color={colors.gold.text} />
          </View>
          <Text style={styles.pendingTitle}>Transfer in progress</Text>
          <Text style={styles.pendingAmount}>GHS {formatCedis(amountPesewas)}</Text>
          <Text style={styles.pendingDestination}>
            → {PROVIDERS.find(p => p.id === provider)?.label} · {momoNumber}
          </Text>
        </View>

        <View style={styles.pendingStatusCard}>
          <View style={styles.statusRow}>
            <View style={styles.statusDot} />
            <Text style={styles.statusText}>Sent to Paystack</Text>
            <Icon
              name="checkmark"
              size={16}
              color={colors.status.success}
              style={styles.statusIndicator}
            />
          </View>
          <View style={styles.statusConnector} />
          <View style={styles.statusRow}>
            <View style={[styles.statusDot, styles.statusDotPending]} />
            <Text style={styles.statusText}>Paystack → your network</Text>
            <ActivityIndicator
              size="small"
              color={colors.gold.base}
              style={styles.statusIndicator}
            />
          </View>
          <View style={styles.statusConnector} />
          <View style={styles.statusRow}>
            <View style={[styles.statusDot, styles.statusDotWaiting]} />
            <Text style={[styles.statusText, styles.statusTextMuted]}>Arrives on your phone</Text>
          </View>
        </View>

        {txnRef && (
          <View style={styles.refCard}>
            <Text style={styles.refLabel}>Reference</Text>
            <Text style={styles.refValue} selectable>
              {txnRef}
            </Text>
          </View>
        )}

        <View style={styles.infoBox}>
          <Text style={styles.infoTitle}>What happens next?</Text>
          <Text style={styles.infoBody}>
            Paystack is sending the money to your MoMo number. This usually takes{' '}
            <Text style={styles.infoBold}>5–10 minutes</Text>. You&apos;ll receive a push
            notification when it lands. You don&apos;t need to keep this screen open.
          </Text>
        </View>

        <View style={styles.infoBox}>
          <Text style={styles.infoTitle}>If it doesn&apos;t arrive</Text>
          <Text style={styles.infoBody}>
            Check your vault statement — the transaction will show as PENDING. If it stays PENDING
            for more than 24 hours, contact support with your reference number above.
          </Text>
        </View>

        <PressableScale
          style={styles.cta}
          onPress={() => navigation.navigate('VaultDetail', { vaultId })}
          accessibilityRole="button"
          accessibilityLabel="Back to vault"
        >
          <Text style={styles.ctaText}>Back to vault</Text>
        </PressableScale>

        <TouchableOpacity
          style={styles.homeLink}
          onPress={() => navigation.navigate('Main', { screen: 'Home' })}
          accessibilityRole="button"
        >
          <Text style={styles.homeLinkText}>Go to home screen</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

function SummaryRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={sumStyles.row}>
      <Text style={sumStyles.label}>{label}</Text>
      <Text style={sumStyles.value} numberOfLines={1}>
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
  flex: { flex: 1 },
  content: { paddingHorizontal: spacing.lg, paddingTop: spacing.lg, paddingBottom: spacing['5xl'] },
  pendingContent: { paddingTop: spacing.xl },

  headerWrap: {
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },

  // Amount phase
  balanceCard: {
    borderRadius: radii['2xl'],
    padding: spacing.xl,
    marginBottom: spacing['2xl'],
  },
  balanceLabel: {
    fontSize: 11,
    color: colors.textOnDarkMuted,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: spacing.xs,
  },
  balanceValue: { fontSize: 36, fontWeight: '800', color: colors.textOnDark, letterSpacing: -1.2 },
  balanceSub: { fontSize: 13, color: colors.textOnDarkFaint, marginTop: spacing.xs },

  fieldLabel: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  fieldLabelDest: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginTop: spacing['2xl'],
  },

  amountRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
    marginBottom: spacing.xs,
  },
  ghsPrefix: { fontSize: 20, fontWeight: '600', color: colors.textSecondary },
  amountInput: {
    flex: 1,
    fontSize: 40,
    fontWeight: '800',
    color: colors.textPrimary,
    letterSpacing: -1.5,
    paddingVertical: spacing.xxs,
    borderBottomWidth: 2,
    borderBottomColor: colors.borderStrong,
  },
  amountInputError: { borderBottomColor: colors.status.error },
  afterBalance: {
    fontSize: 13,
    color: colors.status.successText,
    fontWeight: '600',
    marginBottom: spacing.lg,
  },

  fieldError: { fontSize: 12, color: colors.status.error, marginBottom: spacing.sm },

  providerRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    marginBottom: spacing.sm,
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
    paddingVertical: Platform.OS === 'ios' ? 14 : 11,
    fontSize: 15,
    color: colors.textPrimary,
    marginBottom: spacing.lg,
  },

  // Confirm phase
  summaryCard: {
    borderRadius: radii['2xl'],
    padding: spacing['2xl'],
    marginBottom: spacing.lg,
  },
  summaryHeading: {
    fontSize: 12,
    color: colors.textOnDarkMuted,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: spacing.sm,
  },
  summaryAmount: {
    fontSize: 44,
    fontWeight: '800',
    color: colors.textOnDark,
    letterSpacing: -2,
    marginBottom: spacing.xl,
  },
  summaryDivider: {
    height: 1,
    backgroundColor: 'rgba(255,255,255,0.12)',
    marginBottom: spacing.md,
  },

  // Pending phase
  pendingHero: { alignItems: 'center', marginBottom: spacing['2xl'] },
  pendingIconBadge: {
    width: 76,
    height: 76,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.md,
  },
  successIconBadge: {
    width: 76,
    height: 76,
    borderRadius: radii.pill,
    backgroundColor: colors.status.successBg,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.md,
  },
  pendingTitle: {
    fontSize: 22,
    fontWeight: '800',
    color: colors.textPrimary,
    marginBottom: spacing.sm,
  },
  pendingAmount: {
    fontSize: 36,
    fontWeight: '800',
    color: colors.gold.text,
    letterSpacing: -1.2,
    marginBottom: spacing.xs,
  },
  pendingDestination: { fontSize: 14, color: colors.textSecondary, fontWeight: '500' },

  pendingStatusCard: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    padding: spacing.xl,
    marginBottom: spacing.lg,
    borderWidth: 1,
    borderColor: colors.border,
  },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  statusDot: { width: 12, height: 12, borderRadius: 6, backgroundColor: colors.status.success },
  statusDotPending: { backgroundColor: colors.gold.base },
  statusDotWaiting: { backgroundColor: colors.neutral[300] },
  statusText: { fontSize: 13, fontWeight: '600', color: colors.textPrimary, flex: 1 },
  statusTextMuted: { color: colors.textSecondary },
  statusIndicator: { marginLeft: 'auto' },
  statusConnector: {
    width: 2,
    height: 18,
    backgroundColor: colors.neutral[200],
    marginLeft: 5,
    marginVertical: spacing.xs,
  },

  refCard: {
    backgroundColor: colors.neutral[100],
    borderRadius: radii.md,
    padding: spacing.md,
    marginBottom: spacing.lg,
    alignItems: 'center',
  },
  refLabel: {
    fontSize: 11,
    color: colors.textSecondary,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: spacing.xs,
  },
  refValue: {
    fontSize: 13,
    fontFamily: Platform.OS === 'ios' ? 'Courier New' : 'monospace',
    color: colors.textPrimary,
    fontWeight: '600',
  },

  infoBox: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    padding: spacing.md,
    marginBottom: spacing.md,
    borderWidth: 1,
    borderColor: colors.border,
  },
  infoTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: colors.textPrimary,
    marginBottom: spacing.xs,
  },
  infoBody: { fontSize: 13, color: colors.textSecondary, lineHeight: 19 },
  infoBold: { fontWeight: '700', color: colors.textPrimary },

  homeLink: { marginTop: spacing.md, alignItems: 'center' },
  homeLinkText: { fontSize: 14, color: colors.textSecondary, textDecorationLine: 'underline' },

  cta: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingVertical: 15,
    alignItems: 'center',
  },
  ctaDisabled: { backgroundColor: colors.neutral[300] },
  ctaText: { fontSize: 16, fontWeight: '700', color: colors.neutral[900] },
});

const sumStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: spacing.sm,
  },
  label: { fontSize: 13, color: colors.textOnDarkMuted, flex: 1 },
  value: { fontSize: 14, fontWeight: '700', color: colors.textOnDark, flex: 2, textAlign: 'right' },
});
