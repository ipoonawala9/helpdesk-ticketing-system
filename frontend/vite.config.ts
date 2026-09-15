/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [react()],
    server: {
      port: 5173,
      // In development the browser calls /api on the Vite server, which forwards
      // to the backend, so no CORS setup is needed locally. Deployed builds call
      // VITE_API_BASE_URL directly instead.
      proxy: {
        '/api': { target: env.DEV_API_PROXY_TARGET || 'http://localhost:8080', changeOrigin: true },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: false,
    },
  }
})
