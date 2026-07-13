import { FadeInUp } from 'react-native-reanimated';

/** Staggered card entrance used across dashboard sections. */
export function fadeInUp(delayMs = 0) {
  return FadeInUp.delay(delayMs).duration(420).springify().damping(18);
}
