import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/http'
import { useAuthStore } from './stores/auth'
import './styles/base.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)

// 세션 만료(401)는 어느 API 에서든 난다. 스토어를 비우고 로그인으로 보낸다.
const auth = useAuthStore(pinia)
setUnauthorizedHandler(() => {
  auth.clear()
  if (router.currentRoute.value.name !== 'login') {
    router.replace({ name: 'login', query: { expired: '1' } })
  }
})

app.mount('#app')
