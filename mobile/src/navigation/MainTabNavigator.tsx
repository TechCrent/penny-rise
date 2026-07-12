import React from 'react';
import { Text, StyleSheet } from 'react-native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import HomeScreen from '../screens/HomeScreen';
import { ExploreScreen } from '../screens/Explore/ExploreScreen';
import { ProfileHomeScreen } from '../screens/Profile/ProfileHomeScreen';

export type MainTabParamList = {
  Home: undefined;
  Explore: undefined;
  Profile: undefined;
};

const Tab = createBottomTabNavigator<MainTabParamList>();

const TAB_ICONS: Record<keyof MainTabParamList, string> = {
  Home: '🏠',
  Explore: '🧭',
  Profile: '👤',
};

const iconStyles = StyleSheet.create({
  active: { fontSize: 20, color: '#4F46E5' },
  inactive: { fontSize: 20, color: '#9CA3AF' },
});

export default function MainTabNavigator() {
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarIcon: ({ focused }) => (
          <Text style={focused ? iconStyles.active : iconStyles.inactive}>
            {TAB_ICONS[route.name as keyof MainTabParamList]}
          </Text>
        ),
        tabBarActiveTintColor: '#4F46E5',
        tabBarInactiveTintColor: '#9CA3AF',
      })}
    >
      <Tab.Screen name="Home" component={HomeScreen} />
      <Tab.Screen name="Explore" component={ExploreScreen} />
      <Tab.Screen name="Profile" component={ProfileHomeScreen} />
    </Tab.Navigator>
  );
}
