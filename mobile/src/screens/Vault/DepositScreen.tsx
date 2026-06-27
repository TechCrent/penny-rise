import React, { useRef, useState, useCallback, useEffect } from 'react';
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
import * as WebBrowser from 'expo-web-browser';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import { extractApiError } from '../../api/client';
import { RootStackParamList } from '../../navigation/RootNavigator';
import { useVaultDetail } from '../../api/hooks/useVaultDetail';
import { useAuth } from '../../hooks/useAuth';
import { useVaultDeposit } from '../../api/hooks/useVaultDeposit';
import { useTransactionPoll } from '../../api/hooks/useTransactionPoll';

// ── Types ──────────────────────────────────────────────────────────────────

type Nav = NativeStackNavigationProp<RootStackParamList, 'Deposit'>;
type Route = RouteProp<RootStackParamList, 'Deposit'>;

type Phase = 'amount' | 'method' | 'confirm' | 'authorising' | 'polling' | 'success' | 'failure';

const QUICK_AMOUNTS_GHS = [10, 20, 50, 100, 200, 500];
const MIN_DEPOSIT_GHS = 1;

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
  return Math.round(parseFloat(ghs || '0') * 100);
}

// ── Provider mapping ───────────────────────────────────────────────────────
const PROVIDERS = [
  { id: 'mtn', label: 'MTN MoMo', color: '#FBB01C' },
  { id: 'vodafone', label: 'Vodafone Cash', color: '#E10A0A' },
  { id: 'airteltigo', label: 'AirtelTigo', color: '#FF6200' },
] as const;

type ProviderId = (typeof PROVIDERS)[number]['id'];

// Plain objects (not StyleSheet.create) — accessed via dynamic key so no-unused-styles
// would flag them if inside StyleSheet.create, and no-inline-styles would flag
// object literals directly in JSX style props.
const PROVIDER_BORDER: Record<ProviderId, { borderColor: string }> = {
  mtn: { borderColor: '#FBB01C' },
  vodafone: { borderColor: '#E10A0A' },
  airteltigo: { borderColor: '#FF6200' },
};
const PROVIDER_LABEL_COLOR: Record<ProviderId, { color: string }> = {
  mtn: { color: '#FBB01C' },
  vodafone: { color: '#E10A0A' },
  airteltigo: { color: '#FF6200' },
};

// ─────────────────────────────────────────────────────────────────────────────

export default function DepositScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const { vaultId } = route.params;

  const { data: vault } = useVaultDetail(vaultId);

  const idempotencyKeyRef = useRef<string>(generateKey());

  const [phase, setPhase] = useState<Phase>('amount');
  const [amountGhs, setAmountGhs] = useState('');
  const [amountError, setAmountError] = useState<string | null>(null);
  const [method, setMethod] = useState<'MOMO' | 'CARD'>('MOMO');
  const [momoNumber, setMomoNumber] = useState(user?.momoNumber ?? '');
  const [provider, setProvider] = useState<ProviderId>('mtn');
  const [serverError, setServerError] = useState<string | null>(null);
  const [txnRef, setTxnRef] = useState<string | null>(null);

  const amountPesewas = ghsToPesewas(amountGhs);

  const { mutateAsync, isPending } = useVaultDeposit(vaultId);

  const { data: polledTxn } = useTransactionPoll(txnRef, phase === 'polling');

  useEffect(() => {
    if (!polledTxn) return;
    if (polledTxn.status === 'COMPLETED') {
      queryClient.invalidateQueries({ queryKey: ['statement'] });
      queryClient.invalidateQueries({ queryKey: ['vaults'] });
      setPhase('success');
    } else if (polledTxn.status === 'FAILED') {
      setPhase('failure');
    }
  }, [polledTxn, queryClient]);

  function validateAmount(): boolean {
    const ghs = parseFloat(amountGhs || '0');
    if (isNaN(ghs) || ghs < MIN_DEPOSIT_GHS) {
      setAmountError(`Minimum deposit is GHS ${MIN_DEPOSIT_GHS}.00`);
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
          payment_method: method,
          ...(method === 'MOMO' && {
            mobile_number: momoNumber,
            mobile_provider: provider,
          }),
        },
        idempotencyKey: idempotencyKeyRef.current,
      });

      setTxnRef(resp.transaction_reference);

      if (resp.authorisation_url) {
        setPhase('authorising');
        await WebBrowser.openBrowserAsync(resp.authorisation_url, {
          toolbarColor: '#1A1A2E',
          showTitle: false,
          enableBarCollapsing: false,
        });
        setPhase('polling');
      } else {
        setPhase('polling');
      }
    } catch (err: unknown) {
      const apiError = extractApiError(err);
      const status = axios.isAxiosError(err) ? err.response?.status : undefined;
      const code = apiError?.code;
      const message = apiError?.message;

      if (code === 'VAULT_CLOSED') {
        setServerError('This vault is closed and cannot accept deposits.');
      } else if (status === 403) {
        setServerError("You don't have permission to deposit into this vault.");
      } else if (message) {
        setServerError(message);
      } else {
        setServerError('Something went wrong. Your account was not charged — please try again.');
      }
    }
  }, [amountPesewas, method, momoNumber, provider, mutateAsync]);

  function renderHeader(title: string, canBack = true) {
    return (
      <View style={styles.header}>
        {canBack ? (
          <TouchableOpacity
            onPress={() => {
              if (phase === 'method') setPhase('amount');
              else if (phase === 'confirm') setPhase('method');
              else navigation.goBack();
            }}
            style={styles.headerBtn}
            accessibilityLabel="Go back"
          >
            <Text style={styles.headerBtnIcon}>←</Text>
          </TouchableOpacity>
        ) : (
          <View style={styles.headerBtn} />
        )}
        <Text style={styles.headerTitle}>{title}</Text>
        <View style={styles.headerBtn} />
      </View>
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // PHASE: amount
  // ═══════════════════════════════════════════════════════════════════
  if (phase === 'amount') {
    return (
      <SafeAreaView style={styles.safe}>
        <KeyboardAvoidingView
          style={styles.flex}
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        >
          {renderHeader('Deposit')}

          <ScrollView
            contentContainerStyle={styles.content}
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
          >
            {vault && (
              <View style={styles.vaultContextCard}>
                <Text style={styles.vaultContextLabel}>Depositing into</Text>
                <Text style={styles.vaultContextName}>{vault.name}</Text>
                <Text style={styles.vaultContextBalance}>
                  Current balance: {vault.balance_cedis ?? '—'} GHS
                </Text>
              </View>
            )}

            <View style={styles.amountBlock}>
              <Text style={styles.ghsPrefix}>GHS</Text>
              <TextInput
                style={styles.amountInput}
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
                accessibilityLabel="Deposit amount in Ghana cedis"
              />
            </View>

            {amountError && <Text style={styles.amountErrorText}>{amountError}</Text>}

            <View style={styles.pillRow}>
              {QUICK_AMOUNTS_GHS.map(q => (
                <TouchableOpacity
                  key={q}
                  style={[styles.pill, amountGhs === String(q) && styles.pillActive]}
                  onPress={() => {
                    setAmountGhs(String(q));
                    setAmountError(null);
                  }}
                  accessibilityRole="button"
                  accessibilityLabel={`Set amount to GHS ${q}`}
                >
                  <Text style={[styles.pillText, amountGhs === String(q) && styles.pillTextActive]}>
                    {q}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            <View style={styles.feeSummary}>
              <FeeRow
                label="Amount"
                value={`GHS ${amountPesewas > 0 ? formatCedis(amountPesewas) : '0.00'}`}
              />
              <FeeRow label="Fee" value="Free" highlight />
              <FeeRow
                label="You'll be charged"
                value={`GHS ${amountPesewas > 0 ? formatCedis(amountPesewas) : '0.00'}`}
                bold
              />
            </View>

            <TouchableOpacity
              style={styles.cta}
              onPress={() => {
                if (validateAmount()) setPhase('method');
              }}
              activeOpacity={0.85}
              accessibilityRole="button"
              accessibilityLabel="Continue to payment method"
            >
              <Text style={styles.ctaText}>Continue</Text>
            </TouchableOpacity>
          </ScrollView>
        </KeyboardAvoidingView>
      </SafeAreaView>
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // PHASE: method
  // ═══════════════════════════════════════════════════════════════════
  if (phase === 'method') {
    return (
      <SafeAreaView style={styles.safe}>
        {renderHeader('Payment method')}

        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          <Text style={styles.sectionHeading}>Pay with</Text>

          <View style={styles.methodCards}>
            {(['MOMO', 'CARD'] as const).map(m => (
              <TouchableOpacity
                key={m}
                style={[styles.methodCard, method === m && styles.methodCardActive]}
                onPress={() => {
                  setMethod(m);
                }}
                accessibilityRole="radio"
                accessibilityState={{ selected: method === m }}
                accessibilityLabel={m === 'MOMO' ? 'Mobile money' : 'Card — coming soon'}
              >
                <Text style={styles.methodIcon}>{m === 'MOMO' ? '📱' : '💳'}</Text>
                <Text style={[styles.methodLabel, method === m && styles.methodLabelActive]}>
                  {m === 'MOMO' ? 'Mobile Money' : 'Card'}
                </Text>
                {m === 'CARD' && (
                  <View style={styles.comingSoonPill}>
                    <Text style={styles.comingSoonText}>Coming soon</Text>
                  </View>
                )}
              </TouchableOpacity>
            ))}
          </View>

          {method === 'MOMO' && (
            <View style={styles.momoSection}>
              <Text style={styles.sectionHeading}>MoMo details</Text>

              <View style={styles.providerRow}>
                {PROVIDERS.map(p => (
                  <TouchableOpacity
                    key={p.id}
                    style={[
                      styles.providerPill,
                      provider === p.id && styles.providerPillActive,
                      provider === p.id && PROVIDER_BORDER[p.id],
                    ]}
                    onPress={() => setProvider(p.id)}
                    accessibilityRole="radio"
                    accessibilityState={{ selected: provider === p.id }}
                    accessibilityLabel={p.label}
                  >
                    <Text
                      style={[
                        styles.providerLabel,
                        provider === p.id && PROVIDER_LABEL_COLOR[p.id],
                      ]}
                    >
                      {p.label}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>

              <Text style={styles.fieldLabelMoMo}>MoMo number</Text>
              <TextInput
                style={styles.input}
                value={momoNumber}
                onChangeText={setMomoNumber}
                keyboardType="phone-pad"
                placeholder="024 000 0000"
                returnKeyType="done"
                accessibilityLabel="Mobile money number"
              />
            </View>
          )}

          <TouchableOpacity
            style={[styles.cta, method === 'CARD' && styles.ctaDisabled]}
            disabled={method === 'CARD'}
            onPress={() => setPhase('confirm')}
            activeOpacity={0.85}
            accessibilityRole="button"
          >
            <Text style={styles.ctaText}>
              {method === 'CARD' ? 'Card payments coming soon' : 'Review deposit'}
            </Text>
          </TouchableOpacity>
        </ScrollView>
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
        {renderHeader('Confirm deposit')}

        <ScrollView contentContainerStyle={styles.content}>
          <View style={styles.summaryCard}>
            <Text style={styles.summaryHeading}>You&apos;re depositing</Text>
            <Text style={styles.summaryAmount}>GHS {formatCedis(amountPesewas)}</Text>
            <View style={styles.summaryDivider} />
            <SummaryRow label="Into" value={vault?.name ?? '—'} />
            <SummaryRow
              label="Via"
              value={method === 'MOMO' ? `${providerLabel} · ${momoNumber}` : 'Card'}
            />
            <SummaryRow label="Fee" value="Free" />
          </View>

          <Text style={styles.confirmNotice}>
            By confirming, you authorise this MoMo charge. You&apos;ll approve the payment on your
            phone when prompted by your network.
          </Text>

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
            accessibilityLabel={`Confirm deposit of GHS ${formatCedis(amountPesewas)}`}
          >
            {isPending ? (
              <ActivityIndicator color="#FFFFFF" size="small" />
            ) : (
              <Text style={styles.ctaText}>Confirm deposit · GHS {formatCedis(amountPesewas)}</Text>
            )}
          </TouchableOpacity>
        </ScrollView>
      </SafeAreaView>
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // PHASE: authorising
  // ═══════════════════════════════════════════════════════════════════
  if (phase === 'authorising') {
    return (
      <SafeAreaView style={styles.safe}>
        {renderHeader('Authorising', false)}
        <View style={styles.waitingCenter}>
          <ActivityIndicator size="large" color={INDIGO} />
          <Text style={styles.waitingTitle}>Opening Paystack</Text>
          <Text style={styles.waitingSubtitle}>
            Complete the payment in the browser that just opened. Return here when you&apos;re done.
          </Text>
        </View>
      </SafeAreaView>
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // PHASE: polling
  // ═══════════════════════════════════════════════════════════════════
  if (phase === 'polling') {
    return (
      <SafeAreaView style={styles.safe}>
        {renderHeader('Processing', false)}
        <View style={styles.waitingCenter}>
          <ActivityIndicator size="large" color={INDIGO} />
          <Text style={styles.waitingTitle}>Processing your deposit</Text>
          <Text style={styles.waitingSubtitle}>
            Confirming with your network. This usually takes under a minute.
          </Text>
          {txnRef && (
            <Text style={styles.refText} selectable>
              Ref: {txnRef}
            </Text>
          )}
        </View>
      </SafeAreaView>
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // PHASE: success
  // ═══════════════════════════════════════════════════════════════════
  if (phase === 'success') {
    return (
      <SafeAreaView style={styles.safe}>
        {renderHeader('Deposit complete', false)}
        <View style={styles.resultCenter}>
          <Text style={styles.successIcon}>✅</Text>
          <Text style={styles.resultTitle}>Deposit successful!</Text>
          <Text style={styles.resultAmount}>GHS {formatCedis(amountPesewas)}</Text>
          <Text style={styles.resultSubtitle}>
            has been added to{'\n'}
            <Text style={styles.resultVaultName}>{vault?.name}</Text>
          </Text>
          {txnRef && (
            <Text style={styles.refText} selectable>
              {txnRef}
            </Text>
          )}
          <TouchableOpacity
            style={styles.ctaSuccess}
            onPress={() => {
              idempotencyKeyRef.current = generateKey();
              navigation.navigate('VaultDetail', { vaultId });
            }}
            accessibilityRole="button"
            accessibilityLabel="Back to vault"
          >
            <Text style={styles.ctaText}>Back to vault</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // PHASE: failure
  // ═══════════════════════════════════════════════════════════════════
  return (
    <SafeAreaView style={styles.safe}>
      {renderHeader('Deposit failed', false)}
      <View style={styles.resultCenter}>
        <Text style={styles.failureIcon}>❌</Text>
        <Text style={styles.resultTitle}>Deposit wasn&apos;t completed</Text>
        <Text style={styles.failureSubtitle}>
          {polledTxn?.status === 'FAILED'
            ? 'Your network declined the charge. No money was taken.'
            : 'Something went wrong. Your account was not charged.'}
        </Text>
        {txnRef && (
          <Text style={styles.refText} selectable>
            {txnRef}
          </Text>
        )}
        <TouchableOpacity
          style={styles.ctaFailure}
          onPress={() => {
            setPhase('confirm');
            setServerError(null);
          }}
          accessibilityRole="button"
          accessibilityLabel="Try again"
        >
          <Text style={styles.ctaText}>Try again</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.cancelLink}
          onPress={() => navigation.navigate('VaultDetail', { vaultId })}
          accessibilityRole="button"
        >
          <Text style={styles.cancelLinkText}>Cancel — go back to vault</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

function FeeRow({
  label,
  value,
  bold,
  highlight,
}: {
  label: string;
  value: string;
  bold?: boolean;
  highlight?: boolean;
}) {
  return (
    <View style={feeStyles.row}>
      <Text style={feeStyles.label}>{label}</Text>
      <Text style={[feeStyles.value, bold && feeStyles.bold, highlight && feeStyles.highlight]}>
        {value}
      </Text>
    </View>
  );
}

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

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: BACKGROUND },
  flex: { flex: 1 },
  content: { paddingHorizontal: 16, paddingTop: 16, paddingBottom: 48 },

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
  vaultContextCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 14,
    padding: 14,
    marginBottom: 24,
    borderWidth: 1,
    borderColor: '#EDEDF0',
  },
  vaultContextLabel: {
    fontSize: 11,
    color: MUTED,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: 4,
  },
  vaultContextName: { fontSize: 16, fontWeight: '700', color: DARK, marginBottom: 2 },
  vaultContextBalance: { fontSize: 12, color: MUTED },

  amountBlock: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
  },
  ghsPrefix: { fontSize: 24, fontWeight: '600', color: MUTED, marginRight: 8, marginTop: 8 },
  amountInput: {
    fontSize: 56,
    fontWeight: '800',
    color: DARK,
    letterSpacing: -2,
    minWidth: 120,
    textAlign: 'center',
  },
  amountErrorText: { color: '#DC2626', fontSize: 13, textAlign: 'center', marginBottom: 8 },

  pillRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    justifyContent: 'center',
    marginBottom: 24,
  },
  pill: {
    borderWidth: 1.5,
    borderColor: '#D1D5DB',
    borderRadius: 10,
    paddingHorizontal: 16,
    paddingVertical: 9,
  },
  pillActive: { borderColor: INDIGO, backgroundColor: '#EEF2FF' },
  pillText: { fontSize: 14, fontWeight: '600', color: MUTED },
  pillTextActive: { color: INDIGO },

  feeSummary: {
    backgroundColor: '#FFFFFF',
    borderRadius: 14,
    marginBottom: 28,
    borderWidth: 1,
    borderColor: '#EDEDF0',
    overflow: 'hidden',
  },

  // Method phase
  sectionHeading: {
    fontSize: 14,
    fontWeight: '700',
    color: MUTED,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 12,
  },
  methodCards: { flexDirection: 'row', gap: 12, marginBottom: 24 },
  methodCard: {
    flex: 1,
    backgroundColor: '#FFFFFF',
    borderRadius: 14,
    padding: 16,
    alignItems: 'center',
    borderWidth: 2,
    borderColor: '#EDEDF0',
  },
  methodCardActive: { borderColor: INDIGO, backgroundColor: '#F5F3FF' },
  methodIcon: { fontSize: 28, marginBottom: 6 },
  methodLabel: { fontSize: 13, fontWeight: '600', color: MUTED },
  methodLabelActive: { color: INDIGO },
  comingSoonPill: {
    backgroundColor: '#F3F4F6',
    borderRadius: 6,
    paddingHorizontal: 6,
    paddingVertical: 2,
    marginTop: 6,
  },
  comingSoonText: { fontSize: 10, fontWeight: '600', color: MUTED },

  momoSection: { marginBottom: 24 },
  providerRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 4 },
  providerPill: {
    borderWidth: 1.5,
    borderColor: '#D1D5DB',
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 7,
  },
  providerPillActive: { backgroundColor: '#FAFAFA' },
  providerLabel: { fontSize: 12, fontWeight: '600', color: MUTED },

  fieldLabelMoMo: {
    fontSize: 12,
    fontWeight: '600',
    color: DARK,
    marginBottom: 6,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginTop: 16,
  },
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

  // Confirm phase
  summaryCard: {
    backgroundColor: DARK,
    borderRadius: 20,
    padding: 24,
    marginBottom: 20,
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
  confirmNotice: {
    fontSize: 13,
    color: MUTED,
    lineHeight: 19,
    textAlign: 'center',
    marginBottom: 20,
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

  // Waiting / result screens
  waitingCenter: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32 },
  waitingTitle: { fontSize: 20, fontWeight: '700', color: DARK, marginTop: 20, marginBottom: 8 },
  waitingSubtitle: { fontSize: 14, color: MUTED, textAlign: 'center', lineHeight: 21 },

  resultCenter: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32 },
  successIcon: { fontSize: 64, marginBottom: 12 },
  failureIcon: { fontSize: 64, marginBottom: 12 },
  resultTitle: { fontSize: 22, fontWeight: '800', color: DARK, marginBottom: 8 },
  resultAmount: {
    fontSize: 36,
    fontWeight: '800',
    color: GREEN,
    letterSpacing: -1,
    marginBottom: 8,
  },
  resultSubtitle: {
    fontSize: 15,
    color: MUTED,
    textAlign: 'center',
    lineHeight: 22,
    marginBottom: 4,
  },
  resultVaultName: { fontWeight: '700', color: DARK },
  failureSubtitle: {
    fontSize: 14,
    color: MUTED,
    textAlign: 'center',
    lineHeight: 21,
    marginBottom: 16,
  },
  refText: {
    fontSize: 11,
    fontFamily: Platform.OS === 'ios' ? 'Courier New' : 'monospace',
    color: '#9CA3AF',
    marginTop: 8,
    textAlign: 'center',
  },

  cancelLink: { marginTop: 16 },
  cancelLinkText: { fontSize: 14, color: MUTED, textDecorationLine: 'underline' },

  // Shared CTA variants
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
  ctaSuccess: {
    backgroundColor: INDIGO,
    borderRadius: 14,
    paddingVertical: 15,
    marginTop: 32,
    alignItems: 'center',
    shadowColor: INDIGO,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.28,
    shadowRadius: 10,
    elevation: 4,
  },
  ctaFailure: {
    backgroundColor: INDIGO,
    borderRadius: 14,
    paddingVertical: 15,
    marginTop: 28,
    alignItems: 'center',
    shadowColor: INDIGO,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.28,
    shadowRadius: 10,
    elevation: 4,
  },
  ctaText: { fontSize: 16, fontWeight: '700', color: '#FFFFFF' },
});

const feeStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: 14,
    paddingVertical: 11,
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
  },
  label: { fontSize: 13, color: MUTED },
  value: { fontSize: 13, fontWeight: '600', color: DARK },
  bold: { fontSize: 14, fontWeight: '800' },
  highlight: { color: GREEN },
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
