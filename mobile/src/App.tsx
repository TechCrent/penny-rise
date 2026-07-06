import React, { useState } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { AuthProvider } from './auth/AuthContext';
import { AppLockGate } from './auth/AppLockGate';
import RootNavigator, { linking } from './navigation/RootNavigator';
import { configureNotificationHandler } from './features/notifications/notificationHandler';

configureNotificationHandler();

export default function App() {
  const [queryClient] = useState(() => new QueryClient());

  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <AppLockGate>
            <NavigationContainer linking={linking}>
              <RootNavigator />
            </NavigationContainer>
          </AppLockGate>
        </AuthProvider>
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}
