import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Platform } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RouteProp } from '@react-navigation/native';
import type { RootStackParamList } from '../../navigation/RootNavigator';

type Nav = NativeStackNavigationProp<RootStackParamList, 'TransferSuccess'>;
type Route = RouteProp<RootStackParamList, 'TransferSuccess'>;

export function TransferSuccessScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const { result, recipientName } = route.params;

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.card}>
        <Text style={styles.emoji}>✓</Text>
        <Text style={styles.title}>Transfer Sent!</Text>
        <Text style={styles.subtitle} testID="recipient-label">
          To {recipientName}
        </Text>

        <View style={styles.detailBlock}>
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Amount</Text>
            <Text style={[styles.detailValue, detailStyles.mono]} testID="amount">
              GHS {result.amountCedis}
            </Text>
          </View>
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Fee</Text>
            <Text style={[styles.detailValue, detailStyles.mono]} testID="fee">
              GHS {result.feeAmountCedis}
            </Text>
          </View>
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Total Debited</Text>
            <Text style={[styles.detailValue, detailStyles.mono]} testID="total-debited">
              GHS {result.totalDebitedCedis}
            </Text>
          </View>
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Reference</Text>
            <Text style={[styles.detailValue, detailStyles.mono]} testID="reference">
              {result.transactionReference}
            </Text>
          </View>
          {result.freeTransfersRemaining > 0 && (
            <Text style={styles.freeNote} testID="free-note">
              {result.freeTransfersRemaining} free transfer
              {result.freeTransfersRemaining > 1 ? 's' : ''} remaining this month
            </Text>
          )}
        </View>

        <TouchableOpacity
          style={styles.doneBtn}
          onPress={() => navigation.navigate('Main', { screen: 'Home' })}
          testID="done-btn"
        >
          <Text style={styles.doneBtnText}>Done</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#4F46E5',
    justifyContent: 'center',
    padding: 24,
  },
  card: {
    backgroundColor: '#FFFFFF',
    borderRadius: 24,
    padding: 28,
    alignItems: 'center',
    width: '100%',
  },
  emoji: { fontSize: 64, marginBottom: 8 },
  title: { fontSize: 26, fontWeight: '800', color: '#111827', marginBottom: 4 },
  subtitle: { fontSize: 15, color: '#6B7280', marginBottom: 24 },
  detailBlock: { width: '100%', marginBottom: 24 },
  detailRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 8 },
  detailLabel: { fontSize: 14, color: '#6B7280' },
  detailValue: { fontSize: 14, fontWeight: '600', color: '#111827' },
  freeNote: { fontSize: 12, color: '#10B981', marginTop: 8, textAlign: 'center' },
  doneBtn: {
    backgroundColor: '#4F46E5',
    borderRadius: 14,
    paddingVertical: 14,
    alignItems: 'center',
    width: '100%',
  },
  doneBtnText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
});

const detailStyles = StyleSheet.create({
  mono: { fontFamily: Platform.OS === 'ios' ? 'Courier New' : 'monospace' },
});
