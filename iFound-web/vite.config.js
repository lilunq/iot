import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  base: '/aidashboard/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 80,
    host: true,
    proxy: {
      '/iot': {
        target: 'http://192.168.128.191:9006',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/iot/, '')
      },
      '/xian': {
        target: 'http://192.168.128.191:10098',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/xian/, '')
      },
      '/pos': {
        target: 'http://172.12.20.20:18001/v1',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/pos/, '')
      },
      '/keliuApi': {
        target: 'http://172.12.20.30:9090',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/keliuApi/, '')
      },
      '/ragflow': {
        target: 'http://192.168.128.235:21003',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/ragflow/, '')
      },
      '/emsAgent': {
        target: 'http://172.12.20.25/emsAgent',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/emsAgent/, '')
      }
    }
  },
  test: {
    environment: 'jsdom',
    globals: true
  }
})
