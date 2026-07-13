import React from 'react';
import { View, Text, StyleSheet, Platform } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RouteProp } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import Animated, { FadeIn } from 'react-native-reanimated';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { PressableScale } from '../../components/ui';
import { colors, radii, shadows, spacing, typography } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'TransferSuccess'>;
type Route = RouteProp<RootStackParamList, 'TransferSuccess'>;

export function TransferSuccessScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const { result, recipientName } = route.params;

  return (
    <SafeAreaView style={styles.screen}>
      <Animated.View entering={FadeIn.duration(400)} style={styles.card}>
        <View style={styles.iconBadge}>
          <Ionicons name="checkmark-circle" size={40} color={colors.status.success} />
        </View>
        <Text style={styles.title}>Transfer Sent!</Text>
        <Text style={styles.subtitle} testID="recipient-label">
          To {recipientName}
        </Text>

        <View style={styles.detailBlock}>
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Amount</Text>
            <Text style={[styles.detailValue, detailStyles.mono]} testID="amount">
              GHS {result.amountCedis}
            </Text>
          </View>
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Fee</Text>
            <Text style={[styles.detailValue, detailStyles.mono]} testID="fee">
              GHS {result.feeAmountCedis}
            </Text>
          </View>
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Total Debited</Text>
            <Text style={[styles.detailValue, detailStyles.mono]} testID="total-debited">
              GHS {result.totalDebitedCedis}
            </Text>
          </View>
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Reference</Text>
            <Text style={[styles.detailValue, detailStyles.mono]} testID="reference">
              {result.transactionReference}
            </Text>
          </View>
          {result.freeTransfersRemaining > 0 && (
            <Text style={styles.freeNote} testID="free-note">
              {result.freeTransfersRemaining} free transfer
              {result.freeTransfersRemaining > 1 ? 's' : ''} remaining this month
            </Text>
          )}
        </View>

        <PressableScale
          style={styles.doneBtn}
          onPress={() => navigation.navigate('Main', { screen: 'Home' })}
          testID="done-btn"
        >
          <Text style={styles.doneBtnText}>Done</Text>
        </PressableScale>
      </Animated.View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: colors.neutral[900],
    justifyContent: 'center',
    padding: spacing.xl,
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    padding: spacing['2xl'],
    alignItems: 'center',
    width: '100%',
    ...shadows.xl,
  },
  iconBadge: {
    width: 76,
    height: 76,
    borderRadius: radii.pill,
    backgroundColor: colors.status.successBg,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.md,
  },
  title: { ...typography.h1, fontSize: 26, color: colors.textPrimary, marginBottom: spacing.xs },
  subtitle: { fontSize: 15, color: colors.textSecondary, marginBottom: spacing['2xl'] },
  detailBlock: { width: '100%', marginBottom: spacing['2xl'] },
  detailRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: spacing.sm },
  detailLabel: { fontSize: 14, color: colors.textSecondary },
  detailValue: { fontSize: 14, fontWeight: '600', color: colors.textPrimary },
  freeNote: { fontSize: 12, color: colors.status.success, marginTop: spacing.sm, textAlign: 'center' },
  doneBtn: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
    width: '100%',
  },
  doneBtnText: { color: colors.neutral[900], fontSize: 16, fontWeight: '700' },
});

const detailStyles = StyleSheet.create({
  mono: { fontFamily: Platform.OS === 'ios' ? 'Courier New' : 'monospace' },
});
