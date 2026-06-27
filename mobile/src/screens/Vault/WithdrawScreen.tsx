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
import { extractApiError } from '../../api/client';
import { RootStackParamList } from '../../navigation/RootNavigator';
import { useVaultDetail } from '../../api/hooks/useVaultDetail';
import { useAuth } from '../../hooks/useAuth';
import { useVaultWithdrawal } from '../../api/hooks/useVaultWithdrawal';

// ── Types ──────────────────────────────────────────────────────────────────

type Nav = NativeStackNavigationProp<RootStackParamList, 'Withdraw'>;
type Route = RouteProp<RootStackParamList, 'Withdraw'>;

type Phase = 'amount' | 'confirm' | 'pending';

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
    if (!momoNumber.trim()) {
      setAmountError('Enter your MoMo number.');
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
      setPhase('pending');
    } catch (err: unknown) {
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
      <View style={styles.header}>
        <TouchableOpacity
          style={styles.headerBtn}
          onPress={backFn ?? (() => navigation.goBack())}
          accessibilityLabel="Go back"
        >
          <Text style={styles.headerBtnIcon}>←</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{title}</Text>
        <View style={styles.headerBtn} />
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
            <View style={styles.balanceCard}>
              <Text style={styles.balanceLabel}>Available to withdraw</Text>
              <Text style={styles.balanceValue}>
                GHS {vault ? formatCedis(availablePesewas) : '—'}
              </Text>
              <Text style={styles.balanceSub}>{vault?.name}</Text>
            </View>

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
                placeholderTextColor="#D1D5DB"
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
              <View style={styles.exceedsBox}>
                <Text style={styles.exceedsText}>
                  ⚠️ Amount exceeds your balance of GHS {formatCedis(availablePesewas)}
                </Text>
              </View>
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
              returnKeyType="done"
              accessibilityLabel="MoMo destination number"
            />

            <View style={styles.warningBox}>
              <Text style={styles.warningIcon}>⏱</Text>
              <Text style={styles.warningText}>
                MoMo transfers usually arrive within{' '}
                <Text style={styles.warningBold}>5–10 minutes</Text>. Occasionally up to 24 hours
                during network congestion.
              </Text>
            </View>

            <TouchableOpacity
              style={[styles.cta, exceedsBalance && styles.ctaDisabled]}
              disabled={exceedsBalance}
              onPress={() => {
                if (validateAmount()) setPhase('confirm');
              }}
              activeOpacity={0.85}
              accessibilityRole="button"
              accessibilityLabel="Review withdrawal"
            >
              <Text style={styles.ctaText}>Review withdrawal</Text>
            </TouchableOpacity>
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
          <View style={styles.summaryCard}>
            <Text style={styles.summaryHeading}>You&apos;re withdrawing</Text>
            <Text style={styles.summaryAmount}>GHS {formatCedis(amountPesewas)}</Text>
            <View style={styles.summaryDivider} />
            <SummaryRow label="From" value={vault?.name ?? '—'} />
            <SummaryRow label="To" value={`${providerLabel} · ${momoNumber}`} />
            <SummaryRow label="After balance" value={`GHS ${formatCedis(afterPesewas)}`} />
            <SummaryRow label="Fee" value="Free" />
          </View>

          <View style={styles.settlementNote}>
            <Text style={styles.settlementIcon}>⏳</Text>
            <View style={styles.settlementBody}>
              <Text style={styles.settlementTitle}>Takes a few minutes</Text>
              <Text style={styles.settlementDesc}>
                Your money will arrive on {momoNumber} within 5–10 minutes. We&apos;ll notify you
                when it lands.
              </Text>
            </View>
          </View>

          {serverError && (
            <View style={styles.serverErrorBox}>
              <Text style={styles.serverErrorText}>{serverError}</Text>
            </View>
          )}

          <TouchableOpacity
            style={[styles.cta, isPending && styles.ctaDisabled]}
            onPress={handleConfirm}
            disabled={isPending}
            activeOpacity={0.85}
            accessibilityRole="button"
            accessibilityState={{ busy: isPending }}
            accessibilityLabel={`Confirm withdrawal of GHS ${formatCedis(amountPesewas)}`}
          >
            {isPending ? (
              <ActivityIndicator color="#FFFFFF" size="small" />
            ) : (
              <Text style={styles.ctaText}>Withdraw GHS {formatCedis(amountPesewas)}</Text>
            )}
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
          <Text style={styles.pendingIcon}>🚀</Text>
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
            <Text style={styles.statusCheck}>✓</Text>
          </View>
          <View style={styles.statusConnector} />
          <View style={styles.statusRow}>
            <View style={[styles.statusDot, styles.statusDotPending]} />
            <Text style={styles.statusText}>Paystack → your network</Text>
            <ActivityIndicator size="small" color={INDIGO} style={styles.statusIndicator} />
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

        <TouchableOpacity
          style={styles.cta}
          onPress={() => navigation.navigate('VaultDetail', { vaultId })}
          activeOpacity={0.85}
          accessibilityRole="button"
          accessibilityLabel="Back to vault"
        >
          <Text style={styles.ctaText}>Back to vault</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.homeLink}
          onPress={() => navigation.navigate('Home')}
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
const INDIGO = '#4F46E5';
const DARK = '#1A1A2E';
const MUTED = '#6B7280';
const BACKGROUND = '#F8F9FF';
const GREEN = '#059669';
const AMBER = '#D97706';

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: BACKGROUND },
  flex: { flex: 1 },
  content: { paddingHorizontal: 16, paddingTop: 16, paddingBottom: 48 },
  pendingContent: { paddingTop: 24 },

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
  headerTitle: { fontSize: 17, fontWeight: '700', color: DARK },

  // Amount phase
  balanceCard: {
    backgroundColor: DARK,
    borderRadius: 18,
    padding: 20,
    marginBottom: 24,
    shadowColor: DARK,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.14,
    shadowRadius: 12,
    elevation: 4,
  },
  balanceLabel: {
    fontSize: 11,
    color: 'rgba(255,255,255,0.55)',
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: 6,
  },
  balanceValue: { fontSize: 36, fontWeight: '800', color: '#FFFFFF', letterSpacing: -1.2 },
  balanceSub: { fontSize: 13, color: 'rgba(255,255,255,0.5)', marginTop: 4 },

  fieldLabel: {
    fontSize: 12,
    fontWeight: '700',
    color: DARK,
    marginBottom: 8,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  fieldLabelDest: {
    fontSize: 12,
    fontWeight: '700',
    color: DARK,
    marginBottom: 8,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginTop: 24,
  },

  amountRow: { flexDirection: 'row', alignItems: 'center', gap: 6, marginBottom: 6 },
  ghsPrefix: { fontSize: 20, fontWeight: '600', color: MUTED },
  amountInput: {
    flex: 1,
    fontSize: 40,
    fontWeight: '800',
    color: DARK,
    letterSpacing: -1.5,
    paddingVertical: 4,
    borderBottomWidth: 2,
    borderBottomColor: '#D1D5DB',
  },
  amountInputError: { borderBottomColor: '#EF4444' },
  afterBalance: { fontSize: 13, color: GREEN, fontWeight: '600', marginBottom: 16 },

  exceedsBox: {
    backgroundColor: '#FEF2F2',
    borderRadius: 10,
    padding: 12,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#FCA5A5',
  },
  exceedsText: { fontSize: 13, color: '#991B1B', fontWeight: '500' },
  fieldError: { fontSize: 12, color: '#DC2626', marginBottom: 12 },

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
    paddingVertical: Platform.OS === 'ios' ? 14 : 11,
    fontSize: 15,
    color: DARK,
    marginBottom: 16,
  },

  warningBox: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 8,
    backgroundColor: '#FFFBEB',
    borderRadius: 12,
    padding: 12,
    borderWidth: 1,
    borderColor: '#FDE68A',
    marginBottom: 24,
  },
  warningIcon: { fontSize: 16, lineHeight: 20 },
  warningText: { flex: 1, fontSize: 13, color: '#78350F', lineHeight: 18 },
  warningBold: { fontWeight: '700' },

  // Confirm phase
  summaryCard: {
    backgroundColor: DARK,
    borderRadius: 20,
    padding: 24,
    marginBottom: 16,
    shadowColor: DARK,
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.16,
    shadowRadius: 14,
    elevation: 5,
  },
  summaryHeading: {
    fontSize: 12,
    color: 'rgba(255,255,255,0.6)',
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: 8,
  },
  summaryAmount: {
    fontSize: 44,
    fontWeight: '800',
    color: '#FFFFFF',
    letterSpacing: -2,
    marginBottom: 20,
  },
  summaryDivider: { height: 1, backgroundColor: 'rgba(255,255,255,0.12)', marginBottom: 16 },

  settlementNote: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
    backgroundColor: '#FFFBEB',
    borderRadius: 14,
    padding: 14,
    borderWidth: 1,
    borderColor: '#FDE68A',
    marginBottom: 20,
  },
  settlementIcon: { fontSize: 20 },
  settlementBody: { flex: 1 },
  settlementTitle: { fontSize: 13, fontWeight: '700', color: AMBER, marginBottom: 3 },
  settlementDesc: { fontSize: 13, color: '#78350F', lineHeight: 18 },

  serverErrorBox: {
    backgroundColor: '#FEF2F2',
    borderRadius: 12,
    padding: 14,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#FCA5A5',
  },
  serverErrorText: { fontSize: 13, color: '#991B1B' },

  // Pending phase
  pendingHero: { alignItems: 'center', marginBottom: 28 },
  pendingIcon: { fontSize: 60, marginBottom: 14 },
  pendingTitle: { fontSize: 22, fontWeight: '800', color: DARK, marginBottom: 8 },
  pendingAmount: {
    fontSize: 36,
    fontWeight: '800',
    color: INDIGO,
    letterSpacing: -1.2,
    marginBottom: 6,
  },
  pendingDestination: { fontSize: 14, color: MUTED, fontWeight: '500' },

  pendingStatusCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#EDEDF0',
  },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  statusDot: { width: 12, height: 12, borderRadius: 6, backgroundColor: GREEN },
  statusDotPending: { backgroundColor: INDIGO },
  statusDotWaiting: { backgroundColor: '#D1D5DB' },
  statusText: { fontSize: 13, fontWeight: '600', color: DARK, flex: 1 },
  statusTextMuted: { color: MUTED },
  statusCheck: { fontSize: 14, color: GREEN, marginLeft: 'auto' },
  statusIndicator: { marginLeft: 'auto' },
  statusConnector: {
    width: 2,
    height: 18,
    backgroundColor: '#E5E7EB',
    marginLeft: 5,
    marginVertical: 4,
  },

  refCard: {
    backgroundColor: '#F3F4F6',
    borderRadius: 12,
    padding: 14,
    marginBottom: 16,
    alignItems: 'center',
  },
  refLabel: {
    fontSize: 11,
    color: MUTED,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: 4,
  },
  refValue: {
    fontSize: 13,
    fontFamily: Platform.OS === 'ios' ? 'Courier New' : 'monospace',
    color: DARK,
    fontWeight: '600',
  },

  infoBox: {
    backgroundColor: '#FFFFFF',
    borderRadius: 14,
    padding: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#EDEDF0',
  },
  infoTitle: { fontSize: 13, fontWeight: '700', color: DARK, marginBottom: 6 },
  infoBody: { fontSize: 13, color: MUTED, lineHeight: 19 },
  infoBold: { fontWeight: '700', color: DARK },

  homeLink: { marginTop: 12, alignItems: 'center' },
  homeLinkText: { fontSize: 14, color: MUTED, textDecorationLine: 'underline' },

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
  ctaDisabled: { backgroundColor: '#A5B4FC', shadowOpacity: 0, elevation: 0 },
  ctaText: { fontSize: 16, fontWeight: '700', color: '#FFFFFF' },
});

const sumStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 10,
  },
  label: { fontSize: 13, color: 'rgba(255,255,255,0.6)', flex: 1 },
  value: { fontSize: 14, fontWeight: '700', color: '#FFFFFF', flex: 2, textAlign: 'right' },
});
