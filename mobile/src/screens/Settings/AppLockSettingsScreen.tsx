import React, { useEffect, useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Switch, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import * as LocalAuthentication from 'expo-local-authentication';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { isAppLockEnabled, setAppLockEnabled } from '../../auth/appLock';

type Nav = NativeStackNavigationProp<RootStackParamList, 'AppLockSettings'>;

export function AppLockSettingsScreen() {
  const navigation = useNavigation<Nav>();
  const [loading, setLoading] = useState(true);
  const [enabled, setEnabled] = useState(false);
  const [hardwareAvailable, setHardwareAvailable] = useState(true);
  const [error, setError] = useState<string | undefined>(undefined);

  useEffect(() => {
    (async () => {
      const [currentlyEnabled, hasHardware, isEnrolled] = await Promise.all([
        isAppLockEnabled(),
        LocalAuthentication.hasHardwareAsync(),
        LocalAuthentication.isEnrolledAsync(),
      ]);
      setEnabled(currentlyEnabled);
      setHardwareAvailable(hasHardware && isEnrolled);
      setLoading(false);
    })();
  }, []);

  const onToggle = async (value: boolean) => {
    setError(undefined);

    if (value) {
      const result = await LocalAuthentication.authenticateAsync({
        promptMessage: 'Confirm it’s you to enable App Lock',
      });
      if (!result.success) {
        setError('Could not verify your identity. App Lock was not enabled.');
        return;
      }
    }

    await setAppLockEnabled(value);
    setEnabled(value);
  };

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} hitSlop={8}>
          <Text style={styles.backText}>← Back</Text>
        </TouchableOpacity>
      </View>

      <Text style={styles.heading}>App Lock</Text>
      <Text style={styles.subheading}>
        Require your device biometrics or passcode to open Stash after it&apos;s been backgrounded.
      </Text>

      {loading ? (
        <ActivityIndicator style={styles.loading} />
      ) : !hardwareAvailable ? (
        <Text style={styles.unavailableText}>
          No biometrics or device passcode is set up on this device. Set one up in your device
          settings to use App Lock.
        </Text>
      ) : (
        <View style={styles.row}>
          <Text style={styles.rowLabel}>Require unlock on launch</Text>
          <Switch value={enabled} onValueChange={onToggle} />
        </View>
      )}

      {error ? <Text style={styles.errorText}>{error}</Text> : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#FFFFFF', paddingHorizontal: 24 },
  header: { marginBottom: 24, marginTop: 8 },
  backText: { color: '#1A1A1A', fontSize: 15 },
  heading: { fontSize: 24, fontWeight: '700', color: '#111827', marginBottom: 8 },
  subheading: { fontSize: 14, color: '#6B7280', marginBottom: 28, lineHeight: 20 },
  loading: { marginTop: 20 },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 14,
    borderTopWidth: 1,
    borderBottomWidth: 1,
    borderColor: '#E5E7EB',
  },
  rowLabel: { fontSize: 15, color: '#111827', fontWeight: '500' },
  unavailableText: { fontSize: 13, color: '#9CA3AF', lineHeight: 19 },
  errorText: { color: '#EF4444', fontSize: 13, marginTop: 16 },
});
