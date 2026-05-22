import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const proxyTarget = process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/admin': {
        target: proxyTarget,
        changeOrigin: true,
        secure: false,
        ws: true,
      },
      '/v1': {
        target: proxyTarget,
        changeOrigin: true,
        secure: false,
        ws: true,
      },
      '/anthropic': {
        target: proxyTarget,
        changeOrigin: true,
        secure: false,
        ws: true,
      },
      '/google': {
        target: proxyTarget,
        changeOrigin: true,
        secure: false,
        ws: true,
      },
      '/portal': {
        target: proxyTarget,
        changeOrigin: true,
        secure: false,
        ws: true,
        bypass(req) {
          if (req.headers.accept?.includes('text/html')) {
            return req.url
          }
          return undefined
        },
      },
      '/public': {
        target: proxyTarget,
        changeOrigin: true,
        secure: false,
        ws: true,
        bypass(req) {
          if (req.headers.accept?.includes('text/html')) {
            return req.url
          }
          return undefined
        },
      },
    },
  },
})
