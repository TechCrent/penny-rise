import React, { useEffect, useRef } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useAuth } from '../auth/AuthContext';
import { resolvePostAuthNavigation } from '../navigation/resolvePostAuthNavigation';
import type { RootStackParamList } from '../navigation/RootNavigator';

type Nav = NativeStackNavigationProp<RootStackParamList, 'AuthenticatedBootstrap'>;

export default function AuthenticatedBootstrapScreen() {
  const navigation = useNavigation<Nav>();
  const { kycStatus } = useAuth();
  const startedRef = useRef(false);

  useEffect(() => {
    if (!kycStatus || startedRef.current) return;
    startedRef.current = true;

    resolvePostAuthNavigation(kycStatus).then(route => {
      navigation.reset({ index: 0, routes: [route] });
    });
  }, [kycStatus, navigation]);

  return (
    <View style={styles.loading}>
      <ActivityIndicator size="large" />
    </View>
  );
}

const styles = StyleSheet.create({
  loading: { flex: 1, justifyContent: 'center', alignItems: 'center' },
});
