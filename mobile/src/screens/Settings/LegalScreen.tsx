import React from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Legal'>;

/**
 * Placeholder Terms of Service / Privacy Policy copy — no legal content
 * exists anywhere in the app yet. This exists so the signup consent
 * checkbox and Settings' "Legal" row have somewhere to link to; replace
 * with real copy once legal has signed off.
 */
export function LegalScreen() {
  const navigation = useNavigation<Nav>();

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} hitSlop={8}>
          <Text style={styles.backText}>← Back</Text>
        </TouchableOpacity>
        <Text style={styles.title}>Terms &amp; Privacy</Text>
        <View style={styles.headerSpacer} />
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.placeholderBanner}>
          <Text style={styles.placeholderText}>
            Placeholder copy — pending final review from legal. This is not the binding Terms of
            Service or Privacy Policy.
          </Text>
        </View>

        <Text style={styles.sectionHeading}>Terms of Service</Text>
        <Text style={styles.body}>
          By using Stash, you agree to save responsibly, keep your account credentials secure, and
          provide accurate information during signup and identity verification. Stash reserves the
          right to suspend accounts that violate these terms or applicable law.
        </Text>

        <Text style={styles.sectionHeading}>Privacy Policy</Text>
        <Text style={styles.body}>
          Stash collects the information you provide at signup, KYC verification documents, and
          transaction data needed to operate your vaults, susu groups, and transfers. We do not sell
          your personal data. Data is retained as required for regulatory and operational purposes.
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#FFFFFF' },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingTop: 8,
    paddingBottom: 16,
  },
  backText: { color: '#1A1A1A', fontSize: 15 },
  title: { fontSize: 16, fontWeight: '700', color: '#111827' },
  headerSpacer: { width: 44 },
  content: { paddingHorizontal: 24, paddingBottom: 48 },
  placeholderBanner: {
    backgroundColor: '#FEF3C7',
    borderRadius: 10,
    padding: 14,
    marginBottom: 24,
  },
  placeholderText: { color: '#92400E', fontSize: 13, lineHeight: 19 },
  sectionHeading: {
    fontSize: 18,
    fontWeight: '700',
    color: '#111827',
    marginBottom: 8,
    marginTop: 8,
  },
  body: { fontSize: 14, color: '#374151', lineHeight: 21, marginBottom: 20 },
});
