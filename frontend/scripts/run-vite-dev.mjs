import { createServer } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')

const server = await createServer({
  root,
  plugins: [react()],
  resolve: {
    alias: {
      '@': resolve(root, './src'),
      '@components': resolve(root, './src/components'),
      '@pages': resolve(root, './src/pages'),
      '@services': resolve(root, './src/services'),
      '@store': resolve(root, './src/store'),
      '@utils': resolve(root, './src/utils'),
      '@hooks': resolve(root, './src/hooks'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  configFile: false,
})

await server.listen()
server.printUrls()
