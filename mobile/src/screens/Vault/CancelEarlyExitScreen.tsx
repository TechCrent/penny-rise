import React, { useCallback, useState } from 'react';
import {
  ActivityIndicator,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { extractApiError } from '../../api/client';
import { RootStackParamList } from '../../navigation/RootNavigator';
import { useVaultDetail } from '../../api/hooks/useVaultDetail';
import { useCancelEarlyExit } from '../../api/hooks/useEarlyExit';
import { Banner, Icon, PressableScale, ScreenHeader } from '../../components/ui';
import { colors, radii, spacing } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'CancelEarlyExit'>;
type Route = RouteProp<RootStackParamList, 'CancelEarlyExit'>;

// ─────────────────────────────────────────────────────────────────────────────
// CancelEarlyExitScreen
// ─────────────────────────────────────────────────────────────────────────────
export default function CancelEarlyExitScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const { vaultId } = route.params;

  const { data: vault } = useVaultDetail(vaultId);
  const { mutateAsync: cancelExit, isPending } = useCancelEarlyExit(vaultId);

  const [serverError, setServerError] = useState<string | null>(null);

  const handleCancel = useCallback(async () => {
    setServerError(null);
    try {
      await cancelExit();
      navigation.navigate('VaultDetail', { vaultId, successMessage: 'Early exit cancelled.' });
    } catch (err: unknown) {
      console.error(err);
      const apiError = extractApiError(err);
      if (apiError?.code === 'VAULT_NO_PENDING_EARLY_EXIT') {
        setServerError('This exit request has already been processed and cannot be cancelled.');
      } else {
        setServerError(apiError?.message ?? 'Could not cancel — please try again.');
      }
    }
  }, [cancelExit, navigation, vaultId]);

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.headerWrap}>
        <ScreenHeader title="Cancel early exit" onBack={() => navigation.goBack()} />
      </View>

      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.introCard}>
          <View style={styles.introIconWrap}>
            <Icon name="arrow-undo-outline" size={26} color={colors.gold.text} />
          </View>
          <Text style={styles.introTitle}>Cancel this exit request?</Text>
          <Text style={styles.introBody}>
            {vault?.name ?? 'Your vault'} will return to{' '}
            <Text style={styles.introBold}>ACTIVE</Text>, the lock stays in place, and{' '}
            <Text style={styles.introBold}>no fee will be charged</Text>.
          </Text>
        </View>

        {serverError && <Banner tone="error" message={serverError} />}

        <PressableScale
          style={[styles.cta, isPending && styles.ctaDisabled]}
          onPress={handleCancel}
          disabled={isPending}
          accessibilityRole="button"
          accessibilityState={{ busy: isPending }}
          accessibilityLabel="Cancel early exit request"
        >
          {isPending ? (
            <ActivityIndicator color={colors.neutral[900]} size="small" />
          ) : (
            <Text style={styles.ctaText}>Cancel early exit</Text>
          )}
        </PressableScale>

        <TouchableOpacity
          style={styles.keepLink}
          onPress={() => navigation.goBack()}
          accessibilityRole="button"
        >
          <Text style={styles.keepLinkText}>Keep my exit request</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Styles
// ─────────────────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  content: { paddingHorizontal: spacing.lg, paddingTop: spacing.lg, paddingBottom: spacing['5xl'] },

  headerWrap: {
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },

  introCard: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    padding: spacing.xl,
    marginTop: spacing.md,
    marginBottom: spacing.xl,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
  },
  introIconWrap: {
    width: 56,
    height: 56,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.sm,
  },
  introTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    textAlign: 'center',
  },
  introBody: { fontSize: 14, color: colors.textSecondary, lineHeight: 20, textAlign: 'center' },
  introBold: { fontWeight: '700', color: colors.textPrimary },

  cta: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingVertical: 15,
    alignItems: 'center',
  },
  ctaDisabled: { backgroundColor: colors.neutral[300] },
  ctaText: { fontSize: 15, fontWeight: '700', color: colors.neutral[900] },

  keepLink: { alignItems: 'center', marginTop: spacing.lg },
  keepLinkText: { fontSize: 14, color: colors.textSecondary, textDecorationLine: 'underline' },
});
