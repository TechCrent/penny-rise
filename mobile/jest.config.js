module.exports = {
  preset: 'jest-expo',
  setupFilesAfterEnv: ['@testing-library/jest-native/extend-expect'],
  maxWorkers: 1,
  forceExit: true,
  transformIgnorePatterns: [
    '/node_modules/(?!(.pnpm|react-native|@react-native|@react-native-community|expo|@expo|@expo-google-fonts|react-navigation|@react-navigation|@sentry/react-native|native-base|lucide-react-native))',
    '/node_modules/react-native-reanimated/plugin/',
  ],
  moduleNameMapper: {
    // lucide-react-native's package "exports" prefer its ESM (.mjs) build via
    // the "react-native"/"import" conditions, but Jest's transform config
    // only covers .js/.jsx/.ts/.tsx — force resolution to the plain CJS
    // build instead so the module can be required without a transform.
    '^lucide-react-native$': '<rootDir>/node_modules/lucide-react-native/dist/cjs/lucide-react-native.js',
  },
};
