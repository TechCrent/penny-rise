import React, { useEffect } from 'react';
import { View, StyleSheet } from 'react-native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';
import HomeScreen from '../screens/HomeScreen';
import { ExploreScreen } from '../screens/Explore/ExploreScreen';
import { ProfileHomeScreen } from '../screens/Profile/ProfileHomeScreen';
import { Icon, type IconName } from '../components/ui';
import { colors, radii, shadows } from '../theme';

export type MainTabParamList = {
  Home: undefined;
  Explore: undefined;
  Profile: undefined;
};

const Tab = createBottomTabNavigator<MainTabParamList>();

const TAB_ICONS: Record<keyof MainTabParamList, IconName> = {
  Home: 'home',
  Explore: 'compass',
  Profile: 'person-circle',
};

function TabIcon({ icon, focused, size }: { icon: IconName; focused: boolean; size: number }) {
  const progress = useSharedValue(focused ? 1 : 0);

  useEffect(() => {
    progress.value = withSpring(focused ? 1 : 0, { damping: 16, stiffness: 220 });
  }, [focused, progress]);

  const pillStyle = useAnimatedStyle(() => ({
    opacity: progress.value,
    transform: [{ scale: 0.7 + progress.value * 0.3 }],
  }));

  const iconStyle = useAnimatedStyle(() => ({
    transform: [{ scale: 1 + progress.value * 0.08 }],
  }));

  return (
    <View style={styles.tabIconWrap}>
      <Animated.View style={[styles.tabPill, pillStyle]} />
      <Animated.View style={iconStyle}>
        <Icon
          name={icon}
          size={size}
          strokeWidth={focused ? 2.4 : 1.8}
          color={focused ? colors.gold.text : colors.textTertiary}
        />
      </Animated.View>
    </View>
  );
}

export default function MainTabNavigator() {
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarIcon: ({ focused, size }) => (
          <TabIcon
            icon={TAB_ICONS[route.name as keyof MainTabParamList]}
            focused={focused}
            size={size}
          />
        ),
        tabBarActiveTintColor: colors.gold.text,
        tabBarInactiveTintColor: colors.textTertiary,
        tabBarStyle: {
          backgroundColor: colors.surface,
          borderTopColor: colors.border,
          borderTopLeftRadius: radii.xl,
          borderTopRightRadius: radii.xl,
          height: 64,
          paddingTop: 8,
          ...shadows.md,
        },
      })}
    >
      <Tab.Screen name="Home" component={HomeScreen} />
      <Tab.Screen name="Explore" component={ExploreScreen} />
      <Tab.Screen name="Profile" component={ProfileHomeScreen} />
    </Tab.Navigator>
  );
}

const styles = StyleSheet.create({
  tabIconWrap: {
    width: 44,
    height: 30,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tabPill: {
    position: 'absolute',
    width: 44,
    height: 30,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
  },
});
