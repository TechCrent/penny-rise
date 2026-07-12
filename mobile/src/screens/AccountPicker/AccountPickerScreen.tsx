import React, { useEffect } from 'react';
import { View, Text, FlatList, TouchableOpacity, ActivityIndicator, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { useVaults } from '../../hooks/useVaults';
import { VaultCard } from '../../components/VaultCard';
import type { VaultListItem } from '../../api/vaults';

type Nav = NativeStackNavigationProp<RootStackParamList, 'AccountPicker'>;
type Route = RouteProp<RootStackParamList, 'AccountPicker'>;

const COPY = {
  DEPOSIT: { title: 'Deposit', helper: 'Choose which vault to deposit into.' },
  WITHDRAW: { title: 'Withdraw', helper: 'Choose which vault to withdraw from.' },
} as const;

export function AccountPickerScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const { mode } = route.params;

  const { data, isLoading } = useVaults();
  const vaults = (data?.vaults ?? []).filter(
    v => v.status === 'ACTIVE' || v.status === 'EARLY_EXIT_PENDING',
  );

  // Zero vaults skips this screen entirely and opens Create Vault instead —
  // replace (not navigate) so Account Picker isn't left in the back-stack.
  useEffect(() => {
    if (!isLoading && vaults.length === 0) {
      navigation.replace('CreateVault');
    }
  }, [isLoading, vaults.length, navigation]);

  function selectVault(vault: VaultListItem) {
    if (mode === 'DEPOSIT') {
      navigation.navigate('Deposit', { vaultId: vault.id });
    } else {
      navigation.navigate('Withdraw', { vaultId: vault.id });
    }
  }

  const { title, helper } = COPY[mode];

  if (isLoading || (vaults.length === 0 && !isLoading)) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <ActivityIndicator size="large" />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe} testID="account-picker-screen">
      <View style={styles.header}>
        <TouchableOpacity
          style={styles.headerBtn}
          onPress={() => navigation.goBack()}
          accessibilityRole="button"
          accessibilityLabel="Go back"
        >
          <Text style={styles.headerBtnIcon}>←</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{title}</Text>
        <View style={styles.headerBtn} />
      </View>

      <Text style={styles.helper}>{helper}</Text>

      <FlatList
        data={vaults}
        keyExtractor={v => v.id}
        contentContainerStyle={styles.list}
        renderItem={({ item }) => <VaultCard vault={item} onPress={() => selectVault(item)} />}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F9FAFB' },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  headerBtn: { width: 40, height: 40, justifyContent: 'center' },
  headerBtnIcon: { fontSize: 22, color: '#1A1A2E' },
  headerTitle: { fontSize: 17, fontWeight: '700', color: '#1A1A2E' },
  helper: { fontSize: 14, color: '#6B7280', paddingHorizontal: 16, marginBottom: 16 },
  list: { paddingHorizontal: 16, paddingBottom: 40 },
});
