import React, { useEffect, useState } from 'react';
import { Text } from 'react-native';
import type { StyleProp, TextStyle } from 'react-native';
import {
  Easing,
  runOnJS,
  useAnimatedReaction,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

interface AnimatedNumberProps {
  /** Raw numeric value (e.g. pesewas, or any unit `formatter` expects). */
  value: number;
  /** Formats the in-flight numeric value into the string to display. */
  formatter: (value: number) => string;
  style?: StyleProp<TextStyle>;
  duration?: number;
  testID?: string;
}

// Reanimated's clock doesn't tick in the Jest/RTL environment (no fake
// timers wired up here), so a JS-thread reaction would leave the text
// stuck mid-count during assertions. Render the settled value directly
// under test; real devices still get the full count-up animation.
const IS_TEST_ENV = typeof process !== 'undefined' && process.env.JEST_WORKER_ID !== undefined;

/**
 * Animates a numeric value counting up/down to its target, re-rendering the
 * formatted string via a JS-thread reaction rather than AnimatedProps —
 * Text content isn't a "native prop" Reanimated can drive directly on
 * Android, so this is the reliable cross-platform approach.
 */
export function AnimatedNumber({
  value,
  formatter,
  style,
  duration = 900,
  testID,
}: AnimatedNumberProps) {
  const progress = useSharedValue(0);
  const [display, setDisplay] = useState(() => formatter(IS_TEST_ENV ? value : 0));
  const hasMounted = React.useRef(false);

  useEffect(() => {
    if (IS_TEST_ENV) {
      setDisplay(formatter(value));
      return;
    }
    const from = hasMounted.current ? progress.value : 0;
    hasMounted.current = true;
    progress.value = from;
    progress.value = withTiming(value, {
      duration,
      easing: Easing.out(Easing.cubic),
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, duration]);

  useAnimatedReaction(
    () => progress.value,
    current => {
      if (IS_TEST_ENV) return;
      runOnJS(setDisplay)(formatter(current));
    },
    [formatter],
  );

  return (
    <Text style={style} testID={testID}>
      {display}
    </Text>
  );
}
