import React, { useState } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { AuthProvider } from './auth/AuthContext';
import { AppLockGate } from './auth/AppLockGate';
import RootNavigator, { linking } from './navigation/RootNavigator';
import { configureNotificationHandler } from './features/notifications/notificationHandler';
import { ErrorBoundary } from './components/ErrorBoundary';
import { OfflineBanner } from './components/OfflineBanner';

configureNotificationHandler();

export default function App() {
  const [queryClient] = useState(() => new QueryClient());

  return (
    <SafeAreaProvider>
      <ErrorBoundary>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <OfflineBanner />
            <AppLockGate>
              <NavigationContainer linking={linking}>
                <RootNavigator />
              </NavigationContainer>
            </AppLockGate>
          </AuthProvider>
        </QueryClientProvider>
      </ErrorBoundary>
    </SafeAreaProvider>
  );
}
