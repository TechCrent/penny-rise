import React from 'react';
import { View, Text, StyleSheet, Platform, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RouteProp } from '@react-navigation/native';
import Animated from 'react-native-reanimated';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { PrimaryButton } from '../../components/PrimaryButton';
import { AnimatedNumber, Banner, Icon, fadeInUp } from '../../components/ui';
import { colors, radii, shadows, spacing, typography } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'TransferSuccess'>;
type Route = RouteProp<RootStackParamList, 'TransferSuccess'>;

function formatGhsFromPesewas(value: number): string {
  return `GHS ${(value / 100).toFixed(2)}`;
}

export function TransferSuccessScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const { result, recipientName } = route.params;

  return (
    <SafeAreaView style={styles.screen}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <Animated.View entering={fadeInUp(40)} style={styles.heroSection}>
          <View style={styles.iconBadge}>
            <Icon name="checkmark-circle" size={48} color={colors.status.success} />
          </View>
          <Text style={styles.title}>Transfer Sent!</Text>
          <Text style={styles.subtitle} testID="recipient-label">
            To {recipientName}
          </Text>
          <AnimatedNumber
            value={result.amount}
            formatter={formatGhsFromPesewas}
            style={styles.heroAmount}
            testID="amount"
          />
        </Animated.View>

        <Animated.View entering={fadeInUp(100)} style={styles.detailCard}>
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
          <View style={[styles.detailRow, styles.detailRowLast]}>
            <Text style={styles.detailLabel}>Reference</Text>
            <Text style={[styles.detailValue, detailStyles.mono]} testID="reference">
              {result.transactionReference}
            </Text>
          </View>
        </Animated.View>

        {result.freeTransfersRemaining > 0 && (
          <Animated.View entering={fadeInUp(130)} style={styles.freeNoteWrap}>
            <Banner
              tone="success"
              message={`${result.freeTransfersRemaining} free transfer${
                result.freeTransfersRemaining > 1 ? 's' : ''
              } remaining this month`}
              testID="free-note"
            />
          </Animated.View>
        )}

        <Animated.View entering={fadeInUp(160)} style={styles.ctaWrap}>
          <PrimaryButton
            title="Done"
            onPress={() => navigation.navigate('Main', { screen: 'Home' })}
            testID="done-btn"
          />
        </Animated.View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: {
    flexGrow: 1,
    justifyContent: 'center',
    paddingHorizontal: spacing.xl,
    paddingTop: spacing['2xl'],
    paddingBottom: spacing['4xl'],
  },
  heroSection: {
    alignItems: 'center',
    marginBottom: spacing.xl,
  },
  iconBadge: {
    width: 88,
    height: 88,
    borderRadius: radii.pill,
    backgroundColor: colors.status.successBg,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.lg,
    ...shadows.md,
  },
  title: {
    ...typography.h1,
    fontSize: 28,
    color: colors.textPrimary,
    marginBottom: spacing.xs,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 15,
    color: colors.textSecondary,
    marginBottom: spacing.lg,
    textAlign: 'center',
  },
  heroAmount: { ...typography.numericHero, color: colors.gold.text },
  detailCard: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
    width: '100%',
    marginBottom: spacing.lg,
    ...shadows.sm,
  },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: spacing.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  detailRowLast: { borderBottomWidth: 0 },
  detailLabel: { ...typography.caption, color: colors.textSecondary },
  detailValue: { ...typography.caption, fontWeight: '600', color: colors.textPrimary },
  freeNoteWrap: { width: '100%', marginBottom: spacing.lg },
  ctaWrap: { width: '100%' },
});

const detailStyles = StyleSheet.create({
  mono: { fontFamily: Platform.OS === 'ios' ? 'Courier New' : 'monospace' },
});
