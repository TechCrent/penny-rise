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
      <View style={styles.header}>
        <TouchableOpacity
          style={styles.headerBtn}
          onPress={() => navigation.goBack()}
          accessibilityLabel="Go back"
        >
          <Text style={styles.headerBtnIcon}>←</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle} numberOfLines={1}>
          Cancel early exit
        </Text>
        <View style={styles.headerBtn} />
      </View>

      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.introCard}>
          <Text style={styles.introIcon}>↩️</Text>
          <Text style={styles.introTitle}>Cancel this exit request?</Text>
          <Text style={styles.introBody}>
            {vault?.name ?? 'Your vault'} will return to{' '}
            <Text style={styles.introBold}>ACTIVE</Text>, the lock stays in place, and{' '}
            <Text style={styles.introBold}>no fee will be charged</Text>.
          </Text>
        </View>

        {serverError && (
          <View style={styles.serverErrorBox}>
            <Text style={styles.serverErrorText}>{serverError}</Text>
          </View>
        )}

        <TouchableOpacity
          style={[styles.cta, isPending && styles.ctaDisabled]}
          onPress={handleCancel}
          disabled={isPending}
          activeOpacity={0.85}
          accessibilityRole="button"
          accessibilityState={{ busy: isPending }}
          accessibilityLabel="Cancel early exit request"
        >
          {isPending ? (
            <ActivityIndicator color="#FFFFFF" size="small" />
          ) : (
            <Text style={styles.ctaText}>Cancel early exit</Text>
          )}
        </TouchableOpacity>

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

const INDIGO = '#4F46E5';
const DARK = '#1A1A2E';
const MUTED = '#6B7280';
const BACKGROUND = '#F8F9FF';

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: BACKGROUND },
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
  headerTitle: { fontSize: 17, fontWeight: '700', color: DARK, flex: 1, textAlign: 'center' },

  introCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 20,
    marginTop: 24,
    marginBottom: 20,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#EDEDF0',
  },
  introIcon: { fontSize: 32, marginBottom: 10 },
  introTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: DARK,
    marginBottom: 8,
    textAlign: 'center',
  },
  introBody: { fontSize: 14, color: MUTED, lineHeight: 20, textAlign: 'center' },
  introBold: { fontWeight: '700', color: DARK },

  serverErrorBox: {
    backgroundColor: '#FEF2F2',
    borderRadius: 12,
    padding: 14,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#FCA5A5',
  },
  serverErrorText: { fontSize: 13, color: '#991B1B' },

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
  ctaText: { fontSize: 15, fontWeight: '700', color: '#FFFFFF' },

  keepLink: { alignItems: 'center', marginTop: 16 },
  keepLinkText: { fontSize: 14, color: MUTED, textDecorationLine: 'underline' },
});
