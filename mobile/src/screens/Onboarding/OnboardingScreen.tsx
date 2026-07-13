import React, { useRef, useState } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Dimensions,
  NativeSyntheticEvent,
  NativeScrollEvent,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import Animated, { FadeIn, useAnimatedStyle, withTiming } from 'react-native-reanimated';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { markOnboardingSeen } from '../../storage/onboardingStorage';
import { PressableScale } from '../../components/ui';
import { colors, radii, spacing, typography } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Onboarding'>;
const { width: SCREEN_WIDTH } = Dimensions.get('window');

interface Slide {
  emoji: string;
  title: string;
  body: string;
}

const SLIDES: Slide[] = [
  {
    emoji: '🎯',
    title: 'Give every goal its own vault',
    body: 'Lock money away for something specific, so it is out of easy reach and out of the "maybe I will just spend it" habit.',
  },
  {
    emoji: '🔄',
    title: 'Save as a group, the susu way',
    body: 'Start or join a rotating savings circle with people you trust. Everyone contributes on schedule, everyone gets their turn.',
  },
  {
    emoji: '💸',
    title: 'Send money to your circle instantly',
    body: 'Pay a susu contribution or send cash straight to someone else who saves with you, no bank trip required.',
  },
  {
    emoji: '🏆',
    title: 'Stay on track, get rewarded',
    body: 'Join savings challenges, earn badges, and watch your progress update in real time as you get closer to your goal.',
  },
];

export default function OnboardingScreen() {
  const navigation = useNavigation<Nav>();
  const [index, setIndex] = useState(0);
  const listRef = useRef<FlatList<Slide>>(null);
  const isLast = index === SLIDES.length - 1;

  const finish = () => {
    void markOnboardingSeen();
    navigation.replace('Welcome');
  };

  const goNext = () => {
    if (isLast) {
      finish();
      return;
    }
    listRef.current?.scrollToIndex({ index: index + 1, animated: true });
  };

  const onScroll = (e: NativeSyntheticEvent<NativeScrollEvent>) => {
    const newIndex = Math.round(e.nativeEvent.contentOffset.x / SCREEN_WIDTH);
    if (newIndex !== index) setIndex(newIndex);
  };

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.skipRow}>
        <TouchableOpacity onPress={finish} hitSlop={8} testID="onboarding-skip">
          <Text style={styles.skipText}>Skip</Text>
        </TouchableOpacity>
      </View>

      <FlatList
        ref={listRef}
        data={SLIDES}
        keyExtractor={item => item.title}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onMomentumScrollEnd={onScroll}
        renderItem={({ item }) => (
          <View style={styles.slide} testID="onboarding-slide">
            <Animated.View entering={FadeIn.duration(400)} style={styles.emojiBadge}>
              <Text style={styles.emoji}>{item.emoji}</Text>
            </Animated.View>
            <Text style={styles.title}>{item.title}</Text>
            <Text style={styles.body}>{item.body}</Text>
          </View>
        )}
      />

      <View style={styles.dotsRow}>
        {SLIDES.map((slide, i) => (
          <Dot key={slide.title} active={i === index} />
        ))}
      </View>

      <View style={styles.footer}>
        <PressableScale
          style={styles.primaryButton}
          onPress={goNext}
          testID="onboarding-next"
          accessibilityRole="button"
        >
          <Text style={styles.primaryButtonText}>{isLast ? 'Get started' : 'Next'}</Text>
        </PressableScale>
      </View>
    </SafeAreaView>
  );
}

function Dot({ active }: { active: boolean }) {
  const animatedStyle = useAnimatedStyle(() => ({
    width: withTiming(active ? 20 : 8, { duration: 200 }),
    backgroundColor: withTiming(active ? colors.gold.base : colors.neutral[200], { duration: 200 }),
  }));
  return <Animated.View style={[styles.dot, animatedStyle]} />;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  skipRow: { alignItems: 'flex-end', paddingHorizontal: spacing.xl, paddingTop: spacing.sm },
  skipText: { color: colors.textSecondary, fontSize: 15, fontWeight: '600' },
  slide: {
    width: SCREEN_WIDTH,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: spacing['3xl'],
  },
  emojiBadge: {
    width: 112,
    height: 112,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing['3xl'],
  },
  emoji: { fontSize: 52 },
  title: {
    ...typography.h2,
    color: colors.textPrimary,
    textAlign: 'center',
    marginBottom: spacing.md,
  },
  body: { fontSize: 15, color: colors.textSecondary, textAlign: 'center', lineHeight: 22 },
  dotsRow: { flexDirection: 'row', justifyContent: 'center', marginBottom: spacing.xl },
  dot: {
    height: 8,
    borderRadius: 4,
    marginHorizontal: spacing.xs,
  },
  footer: { paddingHorizontal: spacing.xl, paddingBottom: spacing.lg },
  primaryButton: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingVertical: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryButtonText: { ...typography.button, color: colors.neutral[900] },
});
