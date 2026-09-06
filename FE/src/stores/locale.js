import { defineStore } from 'pinia'
import { LANGUAGES, messages } from '../i18n'
import { useAuthStore } from './auth'

const KEY = 'foten.ui'

/*
 * 언어는 두 층이다.
 *  - myLang: 회원의 모국어(가입 때 고른 languageCode). 챗봇 답변 contentLocal 이 이 언어다.
 *  - ui:     지금 화면에 보여줄 언어. 'ko' 또는 myLang. 헤더의 LangSwitch 가 이 값을 바꾼다.
 * 정적 문구는 messages[ui] → messages.en → messages.ko 순으로 찾는다.
 * (18개 언어 사전 중 아직 안 채운 것은 영어로 보인다.)
 */
export const useLocaleStore = defineStore('locale', {
  state: () => ({
    ui: readSaved() || 'ko',
    guestLang: 'ko', // 로그인 전(회원 없음) 화면에서 고른 언어
  }),

  getters: {
    myLang() {
      const auth = useAuthStore()
      return auth.member?.languageCode || this.guestLang
    },
    isKorean: (s) => s.ui === 'ko',
    htmlLang: (s) => s.ui,
    myLangLabel() {
      return LANGUAGES.find((l) => l.code === this.myLang)?.label || this.myLang
    },
  },

  actions: {
    // 한국어 ↔ 내 언어
    toggle() {
      this.set(this.ui === 'ko' ? this.myLang : 'ko')
    },
    set(code) {
      this.ui = code
      try {
        localStorage.setItem(KEY, code)
      } catch {
        /* noop */
      }
    },
    setGuestLang(code) {
      this.guestLang = code
      this.set(code)
    },
    // 로그인·가입 직후: 내 언어로 시작한다(디자인 의도 — 로그인에서 고른 언어가 '내 언어').
    startWithMyLang() {
      this.set(this.myLang)
    },

    t(key, vars) {
      const dict = messages[this.ui] || {}
      let text = dict[key] ?? messages.en[key] ?? messages.ko[key] ?? key
      if (vars) {
        for (const [k, v] of Object.entries(vars)) text = text.replaceAll(`{${k}}`, String(v))
      }
      return text
    },
  },
})

function readSaved() {
  try {
    return localStorage.getItem(KEY)
  } catch {
    return null
  }
}
