import { Platform } from 'react-native';
import type { ViewStyle } from 'react-native';

/**
 * Soft, low-opacity "premium" shadows. iOS reads shadowColor/Opacity/
 * Radius/Offset; Android only respects `elevation`, so each level pairs a
 * matching elevation so both platforms feel equally "lifted."
 */
function shadow(opacity: number, radius: number, offsetY: number, elevation: number): ViewStyle {
  return Platform.select<ViewStyle>({
    ios: {
      shadowColor: '#0B1220',
      shadowOpacity: opacity,
      shadowRadius: radius,
      shadowOffset: { width: 0, height: offsetY },
    },
    android: { elevation },
    default: {
      shadowColor: '#0B1220',
      shadowOpacity: opacity,
      shadowRadius: radius,
      shadowOffset: { width: 0, height: offsetY },
      elevation,
    },
  }) as ViewStyle;
}

export const shadows = {
  none: {} as ViewStyle,
  sm: shadow(0.04, 6, 1, 1),
  md: shadow(0.06, 12, 4, 3),
  lg: shadow(0.1, 20, 8, 6),
  xl: shadow(0.16, 32, 16, 12),
} as const;

export type ShadowKey = keyof typeof shadows;
