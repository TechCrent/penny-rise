import { defineConfig } from 'vitest/config';
import path from 'path';

// No @vitejs/plugin-react here — vitest@3.2.6 bundles its own vite@7 types
// that structurally conflict with this app's vite@8 (TS2769 on the plugins
// array). esbuild.jsx: 'automatic' below gets the same automatic-runtime
// JSX transform (matching tsconfig's "jsx": "react-jsx") without needing
// the plugin — Fast Refresh, the plugin's other main feature, isn't
// relevant to a one-shot test run anyway.
export default defineConfig({
  esbuild: {
    jsx: 'automatic',
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  test: {
    environment: 'jsdom',
    include: ['tests/**/*.test.{ts,tsx}'],
    setupFiles: ['./tests/setup.ts'],
    // Component test files spin up their own jsdom environment each; running
    // them concurrently caused real (non-deterministic) timeouts on RTL's
    // findBy*/waitFor calls under CPU contention — observed consistently
    // when running the full suite vs. never when running a file alone.
    // Sequential execution trades a few seconds of wall time for reliability,
    // which matters more for a suite this size.
    fileParallelism: false,
  },
});
