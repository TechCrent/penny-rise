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
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { markOnboardingSeen } from '../../storage/onboardingStorage';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Onboarding'>;

const INDIGO = '#4F46E5';
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
            <View style={styles.emojiBadge}>
              <Text style={styles.emoji}>{item.emoji}</Text>
            </View>
            <Text style={styles.title}>{item.title}</Text>
            <Text style={styles.body}>{item.body}</Text>
          </View>
        )}
      />

      <View style={styles.dotsRow}>
        {SLIDES.map((slide, i) => (
          <View key={slide.title} style={[styles.dot, i === index && styles.dotActive]} />
        ))}
      </View>

      <View style={styles.footer}>
        <TouchableOpacity
          style={styles.primaryButton}
          onPress={goNext}
          activeOpacity={0.85}
          testID="onboarding-next"
        >
          <Text style={styles.primaryButtonText}>{isLast ? 'Get started' : 'Next'}</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#FFFFFF' },
  skipRow: { alignItems: 'flex-end', paddingHorizontal: 24, paddingTop: 8 },
  skipText: { color: '#6B7280', fontSize: 15, fontWeight: '600' },
  slide: {
    width: SCREEN_WIDTH,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
  },
  emojiBadge: {
    width: 112,
    height: 112,
    borderRadius: 56,
    backgroundColor: '#EEF2FF',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 32,
  },
  emoji: { fontSize: 52 },
  title: {
    fontSize: 22,
    fontWeight: '800',
    color: '#111827',
    textAlign: 'center',
    marginBottom: 12,
  },
  body: { fontSize: 15, color: '#6B7280', textAlign: 'center', lineHeight: 22 },
  dotsRow: { flexDirection: 'row', justifyContent: 'center', marginBottom: 24 },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#E5E7EB',
    marginHorizontal: 4,
  },
  dotActive: { backgroundColor: INDIGO, width: 20 },
  footer: { paddingHorizontal: 24, paddingBottom: 16 },
  primaryButton: {
    backgroundColor: INDIGO,
    borderRadius: 12,
    paddingVertical: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryButtonText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
});
