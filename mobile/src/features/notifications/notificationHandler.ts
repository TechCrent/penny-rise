import * as Notifications from 'expo-notifications';

/**
 * Configures how a push notification is presented while the app is in the
 * foreground. Without this, iOS suppresses foreground notifications by
 * default. Call once at app startup (see App.tsx) — safe to call more than
 * once, it just overwrites the handler.
 */
export function configureNotificationHandler(): void {
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: true,
      shouldSetBadge: true,
    }),
  });
}
