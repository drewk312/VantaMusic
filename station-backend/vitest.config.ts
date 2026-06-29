import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    // Only pick up test files directly in src/ — exclude the services/ subdirectory
    // which contains pre-existing Node test-runner TAP tests.
    include: ['src/*.test.ts'],
    exclude: ['src/services/**', 'node_modules/**'],
  },
});
