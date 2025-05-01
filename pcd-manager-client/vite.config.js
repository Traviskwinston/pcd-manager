import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: { // Optional: configure server settings if needed
    port: 3000, // Default frontend port
    proxy: { // Proxy API requests to the backend during development
      '/api': {
        target: 'http://localhost:8080', // Your Spring Boot backend port
        changeOrigin: true,
        // rewrite: (path) => path.replace(/^\/api/, '') // Optional: remove /api prefix
      }
    }
  }
}) 