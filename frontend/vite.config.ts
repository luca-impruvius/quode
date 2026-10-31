/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // Same origin as production (Caddy): /api goes to Spring Boot, paths unchanged.
    // changeOrigin false keeps Host: localhost:5173, so the OAuth callback and the post-login
    // redirect come back to this dev server, not to the backend's port.
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: false } },
    // The logos live in ../design-system, outside the frontend folder.
    fs: { allow: ['.', '../design-system'] },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
});
