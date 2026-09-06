import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

/*
 * 화면 ↔ Figma 프레임
 *   /            00a_스플래시            → 1.5초 후 로그인(세션 있으면 홈/온보딩)
 *   /login       00_로그인 · 00b_언어 선택
 *   /signup      00c_회원가입 (신규)
 *   /onboarding/1~3   01b·02b·03b       → 마지막에 POST /users/me/onboarding 한 번
 *   /home        04c_대시보드
 *   /chat        05_대화 · 홈
 *   /spending    09_소비 내역
 *   /me          12_내 정보
 */
const routes = [
  { path: '/', name: 'splash', component: () => import('../pages/SplashPage.vue') },
  { path: '/login', name: 'login', component: () => import('../pages/auth/LoginPage.vue'), meta: { guest: true } },
  { path: '/signup', name: 'signup', component: () => import('../pages/auth/SignupPage.vue'), meta: { guest: true } },
  {
    path: '/onboarding/:step(1|2|3)',
    name: 'onboarding',
    component: () => import('../pages/onboarding/OnboardingPage.vue'),
    meta: { auth: true },
  },
  { path: '/home', name: 'home', component: () => import('../pages/HomePage.vue'), meta: { auth: true, onboarded: true, nav: 'home' } },
  { path: '/chat', name: 'chat', component: () => import('../pages/ChatPage.vue'), meta: { auth: true, onboarded: true, nav: 'chat' } },
  { path: '/spending', name: 'spending', component: () => import('../pages/SpendingPage.vue'), meta: { auth: true, onboarded: true, nav: 'chat' } },
  { path: '/me', name: 'me', component: () => import('../pages/MePage.vue'), meta: { auth: true, onboarded: true, nav: 'me' } },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

// 인증·온보딩 가드.
//  - meta.auth: 세션이 없으면 로그인으로.
//  - meta.onboarded: 온보딩이 안 끝났으면 온보딩 1단계로. (목표진단 API 가 404 를 내기 전에 여기서 걸러낸다)
//  - meta.guest: 이미 로그인돼 있으면 홈으로.
router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.auth) {
    const ok = await auth.ensureSession()
    if (!ok) return { name: 'login', query: { redirect: to.fullPath } }
    if (to.meta.onboarded && !auth.onboarding?.completed) return { name: 'onboarding', params: { step: 1 } }
  }

  if (to.meta.guest && auth.member) {
    return auth.onboarding?.completed ? { name: 'home' } : { name: 'onboarding', params: { step: 1 } }
  }
  return true
})

export default router
