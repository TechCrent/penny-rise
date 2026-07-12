import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useNetworkStatus } from '../hooks/useNetworkStatus';

/**
 * Sits above the navigator so it's visible no matter which screen is
 * active. Stash moves real money — a user on a subway or with a flaky
 * connection should know their deposit/withdraw/transfer attempt is
 * likely to fail before they submit it, rather than seeing a generic
 * error afterwards.
 */
export function OfflineBanner() {
  const { isOffline } = useNetworkStatus();
  const insets = useSafeAreaInsets();

  if (!isOffline) return null;

  return (
    <View style={[styles.banner, { paddingTop: insets.top + 8 }]} testID="offline-banner">
      <Text style={styles.text}>No internet connection</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    backgroundColor: '#B91C1C',
    paddingBottom: 8,
    alignItems: 'center',
  },
  text: { color: '#FFFFFF', fontSize: 13, fontWeight: '600' },
});
