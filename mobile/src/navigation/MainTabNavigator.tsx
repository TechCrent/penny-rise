import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Ionicons } from '@expo/vector-icons';
import HomeScreen from '../screens/HomeScreen';
import { ExploreScreen } from '../screens/Explore/ExploreScreen';
import { ProfileHomeScreen } from '../screens/Profile/ProfileHomeScreen';
import { colors } from '../theme';

export type MainTabParamList = {
  Home: undefined;
  Explore: undefined;
  Profile: undefined;
};

const Tab = createBottomTabNavigator<MainTabParamList>();

const TAB_ICONS: Record<keyof MainTabParamList, { active: keyof typeof Ionicons.glyphMap; inactive: keyof typeof Ionicons.glyphMap }> = {
  Home: { active: 'home', inactive: 'home-outline' },
  Explore: { active: 'compass', inactive: 'compass-outline' },
  Profile: { active: 'person-circle', inactive: 'person-circle-outline' },
};

export default function MainTabNavigator() {
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarIcon: ({ focused, size }) => {
          const icons = TAB_ICONS[route.name as keyof MainTabParamList];
          return (
            <Ionicons
              name={focused ? icons.active : icons.inactive}
              size={size}
              color={focused ? colors.gold.text : colors.textTertiary}
            />
          );
        },
        tabBarActiveTintColor: colors.gold.text,
        tabBarInactiveTintColor: colors.textTertiary,
        tabBarStyle: {
          backgroundColor: colors.surface,
          borderTopColor: colors.border,
        },
      })}
    >
      <Tab.Screen name="Home" component={HomeScreen} />
      <Tab.Screen name="Explore" component={ExploreScreen} />
      <Tab.Screen name="Profile" component={ProfileHomeScreen} />
    </Tab.Navigator>
  );
}
