import { defineStore } from 'pinia'
import { authApi, onboardingApi } from '../api'
import { useDashboardStore } from './dashboard'
import { useChatStore } from './chat'
import { useOnboardingStore } from './onboarding'
import { useLocaleStore } from './locale'

const KEY = 'foten.member'

// 세션은 서버(JSESSIONID)가 들고 있다. 여기 저장하는 것은 화면에 바로 그릴 회원 정보뿐이고,
// 진짜 로그인 여부는 ensureSession 이 /users/me/onboarding 을 불러 확인한다.
// 스토어는 새로고침 전까지 메모리에 살아 있다. 지우지 않으면 로그아웃 뒤 다른 계정으로 들어왔을 때
// 앞사람의 대화·대시보드·온보딩 입력이 그대로 보인다(서버는 회원별로 거르지만 화면이 옛 데이터를 들고 있다).
function resetUserStores() {
  useDashboardStore().invalidate()
  useChatStore().$reset()
  useOnboardingStore().$reset()
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    member: readSaved(), // { memberId, loginId, name, nationality, languageCode }
    onboarding: null, // { completed, hasResidence, hasFinance, hasGoal }
    checked: false, // 이번 페이지 로드에서 세션을 서버에 확인했는가
  }),

  getters: {
    languageCode: (s) => s.member?.languageCode || 'ko',
    // 화면에서 "○○ 님" 으로 부를 이름. 베트남·네팔식 이름은 마지막 토큰이 이름이다.
    // 마지막 토큰이 한 글자면(시드 "Nguyen Van A") 어색하니 전체 이름을 쓴다.
    firstName: (s) => {
      const full = (s.member?.name || '').trim()
      const last = full.split(/\s+/).pop() || ''
      return last.length > 1 ? last : full
    },
  },

  actions: {
    async login(loginId, password) {
      const member = await authApi.login(loginId, password)
      resetUserStores() // 앞사람 데이터를 지운 뒤에 새 회원을 앉힌다
      this.setMember(member)
      useLocaleStore().startWithMyLang() // 화면 언어도 이 회원의 언어로
      await this.refreshOnboarding()
      return member
    },

    async register(body) {
      // 가입 직후 서버가 세션을 만들어 준다. 온보딩은 아직 없다.
      const member = await authApi.register(body)
      resetUserStores()
      this.setMember(member)
      useLocaleStore().startWithMyLang()
      this.onboarding = { completed: false, hasResidence: false, hasFinance: false, hasGoal: false }
      this.checked = true
      return member
    },

    async logout() {
      try {
        await authApi.logout()
      } finally {
        this.clear()
      }
    },

    // 라우터 가드용. 세션이 살아 있으면 true. 401 이면 인터셉터가 clear() 를 부른다.
    async ensureSession() {
      if (this.checked && this.member && this.onboarding) return true
      try {
        await this.refreshOnboarding()
        this.checked = true
        return !!this.member
      } catch {
        return false
      }
    },

    async refreshOnboarding() {
      this.onboarding = await onboardingApi.status()
      return this.onboarding
    },

    setMember(member) {
      this.member = member
      this.checked = true
      try {
        sessionStorage.setItem(KEY, JSON.stringify(member))
      } catch {
        /* 저장 못 해도 동작에는 지장 없다 */
      }
    },

    // 로그아웃과 401(세션 만료) 이 둘 다 지나는 길이다. 여기서 다른 스토어까지 함께 비운다.
    clear() {
      this.member = null
      this.onboarding = null
      this.checked = false
      resetUserStores()
      try {
        sessionStorage.removeItem(KEY)
      } catch {
        /* noop */
      }
    },
  },
})

function readSaved() {
  try {
    const raw = sessionStorage.getItem(KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}
