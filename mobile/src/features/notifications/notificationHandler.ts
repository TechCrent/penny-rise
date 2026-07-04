import * as Notifications from 'expo-notifications';
import Constants from 'expo-constants';

/**
 * Configures how a push notification is presented while the app is in the
 * foreground. Without this, iOS suppresses foreground notifications by
 * default. Call once at app startup (see App.tsx) — safe to call more than
 * once, it just overwrites the handler.
 * No-ops in Expo Go (SDK 53+ removed remote notification support).
 */
export function configureNotificationHandler(): void {
  if (Constants.appOwnership === 'expo') return;
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: true,
      shouldSetBadge: true,
    }),
  });
}
