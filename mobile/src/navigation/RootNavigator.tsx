import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useAuth } from '../auth/AuthContext';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import RegisterScreen from '../screens/RegisterScreen';
import EmailVerificationPendingScreen from '../screens/EmailVerificationPendingScreen';
import HomeScreen from '../screens/HomeScreen';

export type RootStackParamList = {
  Register: undefined;
  EmailVerificationPending: { email: string };
  Home: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function RootNavigator() {
  const { isLoading, isAuthenticated } = useAuth();

  if (isLoading) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      {isAuthenticated ? (
        <Stack.Screen name="Home" component={HomeScreen} />
      ) : (
        <>
          <Stack.Screen name="Register" component={RegisterScreen} />
          <Stack.Screen
            name="EmailVerificationPending"
            component={EmailVerificationPendingScreen}
          />
        </>
      )}
    </Stack.Navigator>
  );
}

const styles = StyleSheet.create({
  loading: { flex: 1, justifyContent: 'center', alignItems: 'center' },
});
