import React, { useEffect, useRef } from 'react';
import { ActivityIndicator, Image, StyleSheet, View } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import Animated, { FadeIn } from 'react-native-reanimated';
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
import { colors, radii, shadows, spacing } from '../theme';

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
      <Animated.View entering={FadeIn.duration(500)} style={styles.logoBadge}>
        <LinearGradient
          colors={[colors.gold.base, colors.gold.hover]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={styles.logoBadgeInner}
        >
          <Image
            source={require('../../assets/images/splash-icon.png')}
            style={styles.logo}
            resizeMode="contain"
          />
        </LinearGradient>
      </Animated.View>
      <Animated.View entering={FadeIn.delay(180).duration(500)}>
        <ActivityIndicator size="small" color={colors.gold.base} style={styles.spinner} />
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  loading: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: colors.background,
  },
  logoBadge: {
    borderRadius: radii['2xl'],
    ...shadows.lg,
  },
  logoBadgeInner: {
    width: 96,
    height: 96,
    borderRadius: radii['2xl'],
    alignItems: 'center',
    justifyContent: 'center',
  },
  logo: { width: 54, height: 54, tintColor: colors.neutral[900] },
  spinner: { marginTop: spacing['2xl'] },
});
