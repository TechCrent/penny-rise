import * as SecureStore from 'expo-secure-store';

const ONBOARDING_SEEN_KEY = 'pennyrise_onboarding_seen';

export async function hasSeenOnboarding(): Promise<boolean> {
  try {
    const raw = await SecureStore.getItemAsync(ONBOARDING_SEEN_KEY);
    return raw === 'true';
  } catch (err) {
    console.error(err);
    return false;
  }
}

export async function markOnboardingSeen(): Promise<void> {
  await SecureStore.setItemAsync(ONBOARDING_SEEN_KEY, 'true');
}
