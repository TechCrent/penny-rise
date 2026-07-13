import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import Animated from 'react-native-reanimated';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { PrimaryButton } from '../../components/PrimaryButton';
import { Icon, ScreenHeader, fadeInUp } from '../../components/ui';
import { colors, radii, shadows, spacing, typography } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'SusuModernComingSoon'>;

/**
 * "Modern" (fixed-term, get-your-own-savings-back) susu is a concept that
 * was explored in the redesign conversation but never committed to — only
 * Traditional (rotating-payout) susu is real. This screen exists so the
 * group-type choice is visible without pretending Modern actually works.
 */
export function SusuModernComingSoonScreen() {
  const navigation = useNavigation<Nav>();

  return (
    <SafeAreaView style={styles.safe} testID="susu-modern-coming-soon-screen">
      <View style={styles.header}>
        <ScreenHeader onBack={() => navigation.goBack()} />
      </View>

      <View style={styles.body}>
        <Animated.View entering={fadeInUp(40)} style={styles.iconBadge}>
          <Icon name="construct-outline" size={32} color={colors.gold.text} />
        </Animated.View>
        <Animated.View entering={fadeInUp(90)} style={styles.copyBlock}>
          <Text style={styles.title}>Modern susu is coming soon</Text>
          <Text style={styles.description}>
            A fixed-term susu where you save toward your own goal, on your own schedule — no
            rotation, no waiting for your turn. We&apos;re still building this.
          </Text>
        </Animated.View>

        <Animated.View entering={fadeInUp(140)} style={styles.ctaWrap}>
          <PrimaryButton
            title="Create a Traditional susu instead"
            onPress={() => navigation.goBack()}
            accessibilityLabel="Create a Traditional susu instead"
          />
        </Animated.View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  header: { paddingHorizontal: spacing.xl, paddingTop: spacing.md },
  body: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: spacing['3xl'],
  },
  iconBadge: {
    width: 84,
    height: 84,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.xl,
    ...shadows.sm,
  },
  copyBlock: { alignItems: 'center', marginBottom: spacing['2xl'] },
  title: {
    ...typography.h2,
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    textAlign: 'center',
  },
  description: {
    ...typography.body,
    color: colors.textSecondary,
    textAlign: 'center',
    lineHeight: 22,
  },
  ctaWrap: { alignSelf: 'stretch' },
});
