import { useEffect, useState } from 'react';
import NetInfo from '@react-native-community/netinfo';

export function useNetworkStatus(): { isOffline: boolean } {
  const [isOffline, setIsOffline] = useState(false);

  useEffect(() => {
    return NetInfo.addEventListener(state => {
      // isInternetReachable is null while still being determined — don't
      // flash the offline banner during that brief unknown window.
      const offline = state.isConnected === false || state.isInternetReachable === false;
      setIsOffline(offline);
    });
  }, []);

  return { isOffline };
}
