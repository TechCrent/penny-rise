import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { useAuth } from '../auth/AuthContext';

import HomeScreen from '../screens/HomeScreen';
import RegisterScreen from '../screens/RegisterScreen';
import EmailVerificationPendingScreen from '../screens/EmailVerificationPendingScreen';
import LoginScreen from '../screens/LoginScreen';
import ForgotPasswordScreen from '../screens/ForgotPasswordScreen';
import ResetPasswordScreen from '../screens/ResetPasswordScreen';

export type RootStackParamList = {
  Register: undefined;
  Login: { successBanner?: string } | undefined;
  ForgotPassword: undefined;
  ResetPassword: { token: string };
  EmailVerificationPending: { email: string };
  Home: undefined;
  KycFlow: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();

const linking = {
  prefixes: ['stash://', 'https://stash.app'],
  config: {
    screens: {
      ResetPassword: { path: 'reset-password', parse: { token: String } },
      EmailVerificationPending: { path: 'verify-email', parse: { email: String } },
    },
  },
};

export { linking };

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
          <Stack.Screen name="Login" component={LoginScreen} />
          <Stack.Screen name="Register" component={RegisterScreen} />
          <Stack.Screen name="ForgotPassword" component={ForgotPasswordScreen} />
          <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} />
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
