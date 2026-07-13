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
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { extractApiError } from '../../api/client';
import { RootStackParamList } from '../../navigation/RootNavigator';
import { useVaultDetail } from '../../api/hooks/useVaultDetail';
import { useAuth } from '../../hooks/useAuth';
import { useVaultDeposit } from '../../api/hooks/useVaultDeposit';
import { useWalletDeposit } from '../../api/hooks/useWalletDeposit';
import { useTransactionPoll } from '../../api/hooks/useTransactionPoll';
import { PROVIDERS, ProviderId, validateMomoNumber } from '../../constants/momoProviders';
import { PressableScale } from '../../components/ui';
import { colors, radii, spacing, typography } from '../../theme';

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
// Real MoMo network brand colors — not part of the app's design system, kept
// as-is since they identify a specific third-party provider.
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
  const { vaultId } = route.params ?? {};
  const isWalletDeposit = !vaultId;

  const { data: vault } = useVaultDetail(vaultId ?? '');

  const idempotencyKeyRef = useRef<string>(generateKey());
  const lastKeyedPayloadRef = useRef<string | null>(null);

  const [phase, setPhase] = useState<Phase>('amount');
  const [amountGhs, setAmountGhs] = useState('');
  const [amountError, setAmountError] = useState<string | null>(null);
  const [method, setMethod] = useState<'MOMO' | 'CARD'>('MOMO');
  const [momoNumber, setMomoNumber] = useState(user?.momoNumber ?? '');
  const [provider, setProvider] = useState<ProviderId>('mtn');
  const [momoError, setMomoError] = useState<string | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);
  const [txnRef, setTxnRef] = useState<string | null>(null);

  const amountPesewas = ghsToPesewas(amountGhs);

  const vaultDeposit = useVaultDeposit(vaultId ?? '');
  const walletDeposit = useWalletDeposit();
  const { mutateAsync, isPending } = isWalletDeposit ? walletDeposit : vaultDeposit;

  const { data: polledTxn } = useTransactionPoll(txnRef, phase === 'polling');

  useEffect(() => {
    if (!polledTxn) return;
    if (polledTxn.status === 'COMPLETED') {
      queryClient.invalidateQueries({ queryKey: ['statement'] });
      queryClient.invalidateQueries({ queryKey: ['vaults'] });
      queryClient.invalidateQueries({ queryKey: ['wallet-balance'] });
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
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

    const payload = {
      amount: amountPesewas,
      payment_method: method,
      ...(method === 'MOMO' && {
        mobile_number: momoNumber,
        mobile_provider: provider,
      }),
    };

    // Only reuse the idempotency key for a byte-for-byte identical retry (e.g.
    // resubmitting after a network hiccup). Any change to the deposit itself
    // (amount, method, MoMo number/provider) must get a fresh key, otherwise
    // payments-service's IdempotencyFilter sees the same key with a different
    // request hash and rejects it with 422 IDEMPOTENCY_KEY_REUSED.
    const payloadSignature = JSON.stringify(payload);
    if (lastKeyedPayloadRef.current !== payloadSignature) {
      idempotencyKeyRef.current = generateKey();
      lastKeyedPayloadRef.current = payloadSignature;
    }

    try {
      const resp = await mutateAsync({
        payload,
        idempotencyKey: idempotencyKeyRef.current,
      });

      setTxnRef(resp.transaction_reference);

      if (resp.authorisation_url) {
        setPhase('authorising');
        await WebBrowser.openBrowserAsync(resp.authorisation_url, {
          toolbarColor: colors.neutral[900],
          showTitle: false,
          enableBarCollapsing: false,
        });
        setPhase('polling');
      } else {
        setPhase('polling');
      }
    } catch (err: unknown) {
      console.error(err);
      const apiError = extractApiError(err);
      const status = axios.isAxiosError(err) ? err.response?.status : undefined;
      const code = apiError?.code;
      const message = apiError?.message;

      if (code === 'VAULT_CLOSED') {
        setServerError('This vault is closed and cannot accept deposits.');
      } else if (status === 403) {
        setServerError("You don't have permission to deposit into this vault.");
      } else if (status === 409 && message?.toLowerCase().includes('paystack subaccount')) {
        setServerError(
          'Payments are not set up for your account yet. Complete KYC approval and ensure Paystack/RabbitMQ are running, then try again.',
        );
      } else if (status === 502 || status === 503) {
        setServerError(
          'Payment service is unavailable. Make sure the payments service is running on port 8081.',
        );
      } else if (status === 500) {
        setServerError(
          message ??
            'Payment service error. Check PAYSTACK_SECRET_KEY in .env and that payments-service is running.',
        );
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
                placeholderTextColor={colors.neutral[300]}
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

            <PressableScale
              style={styles.cta}
              onPress={() => {
                if (validateAmount()) setPhase('method');
              }}
              accessibilityRole="button"
              accessibilityLabel="Continue to payment method"
            >
              <Text style={styles.ctaText}>Continue</Text>
            </PressableScale>
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
                <Ionicons
                  name={m === 'MOMO' ? 'phone-portrait-outline' : 'card-outline'}
                  size={26}
                  color={method === m ? colors.gold.text : colors.textSecondary}
                  style={styles.methodIcon}
                />
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
                onChangeText={t => {
                  setMomoNumber(t);
                  setMomoError(null);
                }}
                keyboardType="phone-pad"
                placeholder="024 000 0000"
                placeholderTextColor={colors.textTertiary}
                returnKeyType="done"
                accessibilityLabel="Mobile money number"
              />
              {momoError && <Text style={styles.amountErrorText}>{momoError}</Text>}
            </View>
          )}

          <PressableScale
            style={[styles.cta, method === 'CARD' && styles.ctaDisabled]}
            disabled={method === 'CARD'}
            onPress={() => {
              if (method === 'MOMO') {
                const error = validateMomoNumber(provider, momoNumber);
                if (error) {
                  setMomoError(error);
                  return;
                }
              }
              setPhase('confirm');
            }}
            accessibilityRole="button"
          >
            <Text style={styles.ctaText}>
              {method === 'CARD' ? 'Card payments coming soon' : 'Review deposit'}
            </Text>
          </PressableScale>
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
          <LinearGradient
            colors={[colors.heroFrom, colors.heroTo]}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 1 }}
            style={styles.summaryCard}
          >
            <Text style={styles.summaryHeading}>You&apos;re depositing</Text>
            <Text style={styles.summaryAmount}>GHS {formatCedis(amountPesewas)}</Text>
            <View style={styles.summaryDivider} />
            <SummaryRow label="Into" value={isWalletDeposit ? 'Wallet' : (vault?.name ?? '—')} />
            <SummaryRow
              label="Via"
              value={method === 'MOMO' ? `${providerLabel} · ${momoNumber}` : 'Card'}
            />
            <SummaryRow label="Fee" value="Free" />
          </LinearGradient>

          <Text style={styles.confirmNotice}>
            By confirming, you authorise this MoMo charge. You&apos;ll approve the payment on your
            phone when prompted by your network.
          </Text>

          {serverError && (
            <View style={styles.serverErrorBox}>
              <Text style={styles.serverErrorText}>{serverError}</Text>
            </View>
          )}

          <PressableScale
            style={[styles.cta, isPending && styles.ctaDisabled]}
            onPress={handleConfirm}
            disabled={isPending}
            accessibilityRole="button"
            accessibilityState={{ busy: isPending }}
            accessibilityLabel={`Confirm deposit of GHS ${formatCedis(amountPesewas)}`}
          >
            {isPending ? (
              <ActivityIndicator color={colors.neutral[900]} size="small" />
            ) : (
              <Text style={styles.ctaText}>Confirm deposit · GHS {formatCedis(amountPesewas)}</Text>
            )}
          </PressableScale>
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
          <ActivityIndicator size="large" color={colors.gold.base} />
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
          <ActivityIndicator size="large" color={colors.gold.base} />
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
          <View style={styles.successIconBadge}>
            <Ionicons name="checkmark-circle" size={48} color={colors.status.success} />
          </View>
          <Text style={styles.resultTitle}>Deposit successful!</Text>
          <Text style={styles.resultAmount}>GHS {formatCedis(amountPesewas)}</Text>
          <Text style={styles.resultSubtitle}>
            has been added to{'\n'}
            <Text style={styles.resultVaultName}>
              {isWalletDeposit ? 'your wallet' : vault?.name}
            </Text>
          </Text>
          {txnRef && (
            <Text style={styles.refText} selectable>
              {txnRef}
            </Text>
          )}
          <PressableScale
            style={styles.ctaSuccess}
            onPress={() => {
              idempotencyKeyRef.current = generateKey();
              lastKeyedPayloadRef.current = null;
              if (isWalletDeposit) {
                navigation.navigate('Wallet');
              } else {
                navigation.navigate('VaultDetail', { vaultId: vaultId! });
              }
            }}
            accessibilityRole="button"
            accessibilityLabel={isWalletDeposit ? 'Back to wallet' : 'Back to vault'}
          >
            <Text style={styles.ctaText}>
              {isWalletDeposit ? 'Back to wallet' : 'Back to vault'}
            </Text>
          </PressableScale>
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
        <View style={styles.failureIconBadge}>
          <Ionicons name="close-circle" size={48} color={colors.status.error} />
        </View>
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
        <PressableScale
          style={styles.ctaFailure}
          onPress={() => {
            setPhase('confirm');
            setServerError(null);
          }}
          accessibilityRole="button"
          accessibilityLabel="Try again"
        >
          <Text style={styles.ctaText}>Try again</Text>
        </PressableScale>
        <TouchableOpacity
          style={styles.cancelLink}
          onPress={() => {
            if (isWalletDeposit) {
              navigation.navigate('Wallet');
            } else {
              navigation.navigate('VaultDetail', { vaultId: vaultId! });
            }
          }}
          accessibilityRole="button"
        >
          <Text style={styles.cancelLinkText}>
            {isWalletDeposit ? 'Cancel — go back to wallet' : 'Cancel — go back to vault'}
          </Text>
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

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  flex: { flex: 1 },
  content: { paddingHorizontal: spacing.lg, paddingTop: spacing.lg, paddingBottom: spacing['5xl'] },

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
  headerBtn: { width: 40, height: 40, justifyContent: 'center' },
  headerBtnIcon: { fontSize: 22, color: colors.textPrimary },
  headerTitle: { ...typography.h3, color: colors.textPrimary },

  // Amount phase
  vaultContextCard: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    padding: spacing.md,
    marginBottom: spacing['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
  },
  vaultContextLabel: {
    fontSize: 11,
    color: colors.textSecondary,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: spacing.xs,
  },
  vaultContextName: { fontSize: 16, fontWeight: '700', color: colors.textPrimary, marginBottom: 2 },
  vaultContextBalance: { fontSize: 12, color: colors.textSecondary },

  amountBlock: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.sm,
  },
  ghsPrefix: { fontSize: 24, fontWeight: '600', color: colors.textSecondary, marginRight: spacing.sm, marginTop: spacing.sm },
  amountInput: {
    fontSize: 56,
    fontWeight: '800',
    color: colors.textPrimary,
    letterSpacing: -2,
    minWidth: 120,
    textAlign: 'center',
  },
  amountErrorText: { color: colors.status.error, fontSize: 13, textAlign: 'center', marginBottom: spacing.sm },

  pillRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    justifyContent: 'center',
    marginBottom: spacing['2xl'],
  },
  pill: {
    borderWidth: 1.5,
    borderColor: colors.borderStrong,
    borderRadius: radii.md,
    paddingHorizontal: spacing.lg,
    paddingVertical: 9,
  },
  pillActive: { borderColor: colors.gold.base, backgroundColor: colors.gold.light },
  pillText: { fontSize: 14, fontWeight: '600', color: colors.textSecondary },
  pillTextActive: { color: colors.gold.text },

  feeSummary: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    marginBottom: spacing['3xl'],
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
  },

  // Method phase
  sectionHeading: {
    fontSize: 14,
    fontWeight: '700',
    color: colors.textSecondary,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: spacing.md,
  },
  methodCards: { flexDirection: 'row', gap: spacing.md, marginBottom: spacing['2xl'] },
  methodCard: {
    flex: 1,
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    padding: spacing.lg,
    alignItems: 'center',
    borderWidth: 2,
    borderColor: colors.border,
  },
  methodCardActive: { borderColor: colors.gold.base, backgroundColor: colors.gold.light },
  methodIcon: { marginBottom: spacing.xs },
  methodLabel: { fontSize: 13, fontWeight: '600', color: colors.textSecondary },
  methodLabelActive: { color: colors.gold.text },
  comingSoonPill: {
    backgroundColor: colors.neutral[100],
    borderRadius: 6,
    paddingHorizontal: 6,
    paddingVertical: 2,
    marginTop: spacing.xs,
  },
  comingSoonText: { fontSize: 10, fontWeight: '600', color: colors.textSecondary },

  momoSection: { marginBottom: spacing['2xl'] },
  providerRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginBottom: spacing.xs },
  providerPill: {
    borderWidth: 1.5,
    borderColor: colors.borderStrong,
    borderRadius: radii.md,
    paddingHorizontal: spacing.md,
    paddingVertical: 7,
  },
  providerPillActive: { backgroundColor: colors.neutral[50] },
  providerLabel: { fontSize: 12, fontWeight: '600', color: colors.textSecondary },

  fieldLabelMoMo: {
    fontSize: 12,
    fontWeight: '600',
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginTop: spacing.lg,
  },
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

  // Confirm phase
  summaryCard: {
    borderRadius: radii['2xl'],
    padding: spacing['2xl'],
    marginBottom: spacing.xl,
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
  summaryDivider: { height: 1, backgroundColor: 'rgba(255,255,255,0.12)', marginBottom: spacing.md },
  confirmNotice: {
    fontSize: 13,
    color: colors.textSecondary,
    lineHeight: 19,
    textAlign: 'center',
    marginBottom: spacing.xl,
  },
  serverErrorBox: {
    backgroundColor: colors.status.errorBg,
    borderRadius: radii.md,
    padding: spacing.md,
    marginBottom: spacing.md,
    borderWidth: 1,
    borderColor: '#FCA5A5',
  },
  serverErrorText: { fontSize: 13, color: colors.status.errorText },

  // Waiting / result screens
  waitingCenter: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing['3xl'] },
  waitingTitle: { fontSize: 20, fontWeight: '700', color: colors.textPrimary, marginTop: spacing.xl, marginBottom: spacing.sm },
  waitingSubtitle: { fontSize: 14, color: colors.textSecondary, textAlign: 'center', lineHeight: 21 },

  resultCenter: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing['3xl'] },
  successIconBadge: {
    width: 88,
    height: 88,
    borderRadius: radii.pill,
    backgroundColor: colors.status.successBg,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.md,
  },
  failureIconBadge: {
    width: 88,
    height: 88,
    borderRadius: radii.pill,
    backgroundColor: colors.status.errorBg,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.md,
  },
  resultTitle: { fontSize: 22, fontWeight: '800', color: colors.textPrimary, marginBottom: spacing.sm },
  resultAmount: {
    fontSize: 36,
    fontWeight: '800',
    color: colors.status.successText,
    letterSpacing: -1,
    marginBottom: spacing.sm,
  },
  resultSubtitle: {
    fontSize: 15,
    color: colors.textSecondary,
    textAlign: 'center',
    lineHeight: 22,
    marginBottom: spacing.xs,
  },
  resultVaultName: { fontWeight: '700', color: colors.textPrimary },
  failureSubtitle: {
    fontSize: 14,
    color: colors.textSecondary,
    textAlign: 'center',
    lineHeight: 21,
    marginBottom: spacing.lg,
  },
  refText: {
    fontSize: 11,
    fontFamily: Platform.OS === 'ios' ? 'Courier New' : 'monospace',
    color: colors.textTertiary,
    marginTop: spacing.sm,
    textAlign: 'center',
  },

  cancelLink: { marginTop: spacing.lg },
  cancelLinkText: { fontSize: 14, color: colors.textSecondary, textDecorationLine: 'underline' },

  // Shared CTA variants
  cta: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingVertical: 15,
    alignItems: 'center',
  },
  ctaDisabled: { backgroundColor: colors.neutral[300] },
  ctaSuccess: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingVertical: 15,
    marginTop: spacing['2xl'],
    alignItems: 'center',
  },
  ctaFailure: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingVertical: 15,
    marginTop: spacing.xl,
    alignItems: 'center',
  },
  ctaText: { fontSize: 16, fontWeight: '700', color: colors.neutral[900] },
});

const feeStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.md,
    paddingVertical: 11,
    borderBottomWidth: 1,
    borderBottomColor: colors.neutral[100],
  },
  label: { fontSize: 13, color: colors.textSecondary },
  value: { fontSize: 13, fontWeight: '600', color: colors.textPrimary },
  bold: { fontSize: 14, fontWeight: '800' },
  highlight: { color: colors.status.successText },
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
