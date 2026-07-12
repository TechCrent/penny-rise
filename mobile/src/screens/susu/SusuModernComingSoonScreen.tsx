import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';

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
        <TouchableOpacity
          style={styles.headerBtn}
          onPress={() => navigation.goBack()}
          accessibilityRole="button"
          accessibilityLabel="Go back"
        >
          <Text style={styles.headerBtnIcon}>←</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.body}>
        <Text style={styles.icon}>🛠️</Text>
        <Text style={styles.title}>Modern susu is coming soon</Text>
        <Text style={styles.description}>
          A fixed-term susu where you save toward your own goal, on your own schedule — no
          rotation, no waiting for your turn. We&apos;re still building this.
        </Text>

        <TouchableOpacity
          style={styles.cta}
          onPress={() => navigation.goBack()}
          activeOpacity={0.85}
          accessibilityRole="button"
          accessibilityLabel="Create a Traditional susu instead"
        >
          <Text style={styles.ctaText}>Create a Traditional susu instead</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F9FAFB' },
  header: { paddingHorizontal: 16, paddingVertical: 12 },
  headerBtn: { width: 40, height: 40, justifyContent: 'center' },
  headerBtnIcon: { fontSize: 22, color: '#1A1A2E' },
  body: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 32 },
  icon: { fontSize: 56, marginBottom: 16 },
  title: { fontSize: 20, fontWeight: '700', color: '#111827', marginBottom: 8, textAlign: 'center' },
  description: {
    fontSize: 14,
    color: '#6B7280',
    textAlign: 'center',
    lineHeight: 21,
    marginBottom: 28,
  },
  cta: {
    backgroundColor: '#111827',
    borderRadius: 12,
    paddingHorizontal: 24,
    paddingVertical: 14,
  },
  ctaText: { fontSize: 15, fontWeight: '700', color: '#FFFFFF' },
});
