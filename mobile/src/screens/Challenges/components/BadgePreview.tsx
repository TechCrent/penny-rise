import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { colors } from '../../../theme';

interface Props {
  badgeName: string;
  size?: number;
  earned?: boolean; // dims + locks the badge when the user hasn't earned it yet
}

// challenge.badges.asset_name values (e.g. "badge_starter_saver") don't
// resolve to any real image file — mobile/assets/ has no badges/ folder at
// all yet (the seed migration itself flags these as unverified placeholder
// values). Rendering a static require() on a path that doesn't exist would
// fail the Metro bundler, not just show a broken image, so this renders a
// generated placeholder (initial + lock state) instead of an <Image>. Swap
// for real artwork once mobile/assets/badges/ exists.
export function BadgePreview({ badgeName, size = 64, earned = true }: Props) {
  const initial = badgeName.trim().charAt(0).toUpperCase() || '?';

  return (
    <View
      style={[
        styles.circle,
        { width: size, height: size, borderRadius: size / 2 },
        earned ? styles.earned : styles.locked,
      ]}
      testID="badge-preview"
    >
      {earned ? (
        <Text style={[styles.initial, { fontSize: size * 0.4 }]}>{initial}</Text>
      ) : (
        <Text style={{ fontSize: size * 0.35 }}>🔒</Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  circle: { alignItems: 'center', justifyContent: 'center' },
  earned: { backgroundColor: colors.gold.base },
  locked: { backgroundColor: colors.neutral[200] },
  initial: { color: colors.neutral[900], fontWeight: '800' },
});
