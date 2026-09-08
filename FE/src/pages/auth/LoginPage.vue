<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useLocaleStore } from '../../stores/locale'
import { errorKey } from '../../api/http'
import { LANGUAGES } from '../../i18n'
import BaseInput from '../../components/ui/BaseInput.vue'
import BaseButton from '../../components/ui/BaseButton.vue'
import LangToggle from '../../components/ui/LangToggle.vue'
import logo from '../../assets/img/foten_logo.png'

// Figma 00_로그인(217:1856) + 00b_언어 선택(217:2020, LangToggle Open)
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const locale = useLocaleStore()

const loginId = ref('')
const password = ref('')
const error = ref(route.query.expired ? locale.t('login.expired') : '')
const loading = ref(false)

// 로그인 전이라 회원 언어가 없다. 여기서 고른 언어는 화면 문구 언어이자 '내 언어' 후보다.
const guestLang = computed({
  get: () => locale.ui,
  set: (code) => locale.setGuestLang(code),
})
const langList = LANGUAGES.filter((l) => l.code !== 'ko')
  .map((l) => l.label)
  .join(' · ')

async function submit() {
  if (!loginId.value.trim() || !password.value) return
  error.value = ''
  loading.value = true
  try {
    await auth.login(loginId.value.trim(), password.value)
    // 화면 언어: 로그인 화면에서 고른 것이 한국어가 아니면 내 언어로. (내 언어 = 회원 languageCode)
    if (locale.ui !== 'ko') locale.set(auth.languageCode)
    const to = route.query.redirect || (auth.onboarding?.completed ? '/home' : '/onboarding/1')
    router.replace(String(to))
  } catch (e) {
    error.value = e?.response?.status === 401 ? locale.t('login.fail') : locale.t(errorKey(e))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login">
    <div class="toggle">
      <LangToggle v-model="guestLang" />
    </div>

    <form class="body" @submit.prevent="submit">
      <div class="brand">
        <img :src="logo" alt="FO:TEN" class="logo" />
        <p class="tagline">{{ locale.t('login.tagline1') }}<br />{{ locale.t('login.tagline2') }}</p>
      </div>

      <div class="form">
        <BaseInput v-model="loginId" :label="locale.t('login.id')" autocomplete="username" />
        <BaseInput v-model="password" :label="locale.t('login.pw')" type="password" autocomplete="current-password" />
        <p v-if="error" class="error">{{ error }}</p>
      </div>

      <div class="btns">
        <BaseButton type="submit" :loading="loading" :disabled="!loginId || !password">{{ locale.t('login.submit') }}</BaseButton>
        <BaseButton variant="text" @click="router.push({ name: 'signup' })">{{ locale.t('login.signup') }}</BaseButton>
      </div>
    </form>

    <footer class="foot">
      <div class="line" />
      <p class="f1">{{ locale.t('login.langs', { n: LANGUAGES.length }) }}</p>
      <p class="f2">{{ langList }}</p>
    </footer>
  </main>
</template>

<style scoped>
.login {
  position: relative;
  min-height: 100dvh;
  background: #fff;
  display: flex;
  flex-direction: column;
}
.toggle {
  position: absolute;
  right: 20px;
  top: 22px;
}
.body {
  padding: 68px var(--page-x) 0;
  display: flex;
  flex-direction: column;
  flex: 1 0 auto;
}
.brand {
  padding-top: 60px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.logo {
  width: 148px;
  height: 38px;
  object-fit: contain;
}
.tagline {
  font-size: 16px;
  line-height: 1.45;
  color: var(--gray-700);
}
.form {
  padding-top: 44px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.error {
  font-size: 14px;
  color: var(--red);
}
.btns {
  padding-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.foot {
  margin: auto var(--page-x) 48px;
  padding-top: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.line {
  height: 1px;
  background: var(--border-soft);
  margin-bottom: 16px;
}
.f1 {
  font-size: 14px;
  font-weight: 500;
  color: var(--gray-700);
}
.f2 {
  font-size: 12px;
  line-height: 1.45;
  color: var(--gray-400);
}
</style>
