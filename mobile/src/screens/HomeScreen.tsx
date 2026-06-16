import React from 'react';
import { StyleSheet, Text, View } from 'react-native';

/**
 * Placeholder home screen.
 * Business logic and real UI will be added in later milestones.
 */
export default function HomeScreen() {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Stash</Text>
      <Text style={styles.subtitle}>Platform v0.1 — Scaffold</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FFFFFF',
  },
  title: {
    fontSize: 32,
    fontWeight: '700',
    color: '#1A1A1A',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 14,
    color: '#6B7280',
  },
});
