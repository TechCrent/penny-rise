import React, { useEffect, useRef } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useAuth } from '../auth/AuthContext';
import { resolvePostAuthNavigation } from '../navigation/resolvePostAuthNavigation';
import { registerPushToken } from '../features/notifications/pushSetup';
import type { RootStackParamList } from '../navigation/RootNavigator';
import { hasPromptedAppLockSetup } from '../auth/appLock';
import {
  markKycUnderReviewBannerPending,
  clearKycUnderReviewBannerPending,
} from '../storage/kycStorage';
import { colors } from '../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'AuthenticatedBootstrap'>;

export default function AuthenticatedBootstrapScreen() {
  const navigation = useNavigation<Nav>();
  const { kycStatus, justLoggedIn } = useAuth();
  const startedRef = useRef(false);
  const pushRegisteredRef = useRef(false);

  useEffect(() => {
    if (!kycStatus || startedRef.current) return;
    startedRef.current = true;

    resolvePostAuthNavigation(kycStatus).then(async route => {
      // Keep the LoginScreen "under review" banner flag in sync with what we
      // actually just resolved — set it the moment we know review is still
      // pending, clear it the moment we resolve past it (approved) so a
      // later logout/session-expiry doesn't show a stale banner.
      if (route.name === 'KycSubmissionPending') {
        void markKycUnderReviewBannerPending();
      } else if (route.name === 'Main') {
        void clearKycUnderReviewBannerPending();
      }

      // Offer the one-time app-lock opt-in only right after a fresh login
      // (never on cold-start session restore) and only once ever, and only
      // once the user has actually reached the main app (not mid-KYC).
      if (route.name === 'Main' && justLoggedIn && !(await hasPromptedAppLockSetup())) {
        navigation.reset({
          index: 0,
          routes: [{ name: 'AppLockSetupPrompt', params: { nextRoute: route } }],
        });
        return;
      }

      navigation.reset({ index: 0, routes: [route] });
    });
  }, [kycStatus, justLoggedIn, navigation]);

  useEffect(() => {
    // Fires once per authenticated session — this screen mounts both right
    // after login and on a cold start with an already-valid stored session,
    // which covers the AC's "after app launch after login". Fire-and-forget:
    // registerPushToken() never throws and never blocks navigation above.
    if (pushRegisteredRef.current) return;
    pushRegisteredRef.current = true;
    void registerPushToken();
  }, []);

  return (
    <View style={styles.loading}>
      <ActivityIndicator size="large" color={colors.gold.base} />
    </View>
  );
}

const styles = StyleSheet.create({
  loading: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: colors.background },
});
