import * as Notifications from 'expo-notifications';
import Constants from 'expo-constants';
import { Platform } from 'react-native';
import { apiClient } from '../../api/client';

/**
 * Called once, silently, after an authenticated session is established
 * (see wiring in AuthenticatedBootstrapScreen). Per the AC: "never blocks
 * launch or shows an error to the user if it fails" — every failure path
 * here swallows and logs, never throws or surfaces UI.
 */
export async function registerPushToken(): Promise<void> {
  // Remote push notifications removed from Expo Go in SDK 53+.
  // executionEnvironment === 'storeClient' is the reliable SDK 53+ check;
  // appOwnership === 'expo' kept as fallback for older Expo Go versions.
  if (Constants.executionEnvironment === 'storeClient' || Constants.appOwnership === 'expo') return;
  try {
    const { status: existingStatus } = await Notifications.getPermissionsAsync();
    let finalStatus = existingStatus;

    if (existingStatus !== 'granted') {
      const { status } = await Notifications.requestPermissionsAsync();
      finalStatus = status;
    }

    if (finalStatus !== 'granted') {
      // User declined — not an error, just nothing to register. Silent, per AC.
      return;
    }

    const tokenResponse = await Notifications.getExpoPushTokenAsync();
    const token = tokenResponse.data; // "ExponentPushToken[...]" per v0.5-014

    const platform = Platform.OS === 'ios' ? 'IOS' : 'ANDROID';

    await apiClient.post('/api/v1/devices/register', { token, platform });
  } catch (error) {
    // Deliberately swallowed — see this function's doc comment.
    console.warn('[pushSetup] push token registration failed silently', error);
  }
}
