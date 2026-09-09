import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 브라우저는 항상 /api 로만 보낸다. 개발에서는 이 프록시가, Docker 에서는 nginx 가 백엔드로 넘긴다.
// 세션 쿠키(JSESSIONID)가 같은 출처로 오가야 하므로 절대 URL 을 쓰지 않는다.
export default defineConfig({
  plugins: [vue()],
  server: {
    // PORT 환경변수가 있으면 그 포트(프리뷰 도구·컨테이너용), 없으면 5173.
    port: Number(process.env.PORT) || 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
  },
})
