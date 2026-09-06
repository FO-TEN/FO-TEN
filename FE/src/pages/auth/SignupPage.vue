<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useLocaleStore } from '../../stores/locale'
import { errorMessage } from '../../api/http'
import { LANGUAGES, NATIONALITIES, nationalityOf } from '../../i18n'
import BaseInput from '../../components/ui/BaseInput.vue'
import BaseButton from '../../components/ui/BaseButton.vue'
import LangToggle from '../../components/ui/LangToggle.vue'
import logo from '../../assets/img/foten_logo.png'

/*
 * 00c_회원가입 (신규 — 로그인 화면과 같은 틀).
 * POST /api/auth/register: loginId · password(8자+) · name · nationality · languageCode 전부 필수.
 * 가입 직후 세션이 생기므로 바로 온보딩 1단계로 간다.
 */
const router = useRouter()
const auth = useAuthStore()
const locale = useLocaleStore()

const form = ref({ loginId: '', password: '', name: '', nationality: '', languageCode: '' })
const error = ref('')
const loading = ref(false)

const guestLang = computed({
  get: () => locale.ui,
  set: (code) => locale.setGuestLang(code),
})

const nationalityOptions = NATIONALITIES.map((n) => ({ value: n.code, label: locale.t('nation.' + n.code) }))
const languageOptions = LANGUAGES.map((l) => ({ value: l.code, label: l.label }))

// 국적을 고르면 그 나라 언어를 기본으로 채운다. 바꿀 수 있다.
watch(
  () => form.value.nationality,
  (code) => {
    const n = nationalityOf(code)
    if (n && !form.value.languageCode) form.value.languageCode = n.lang
  },
)

const pwShort = computed(() => form.value.password.length > 0 && form.value.password.length < 8)
const valid = computed(() => {
  const f = form.value
  return f.loginId.trim().length > 0 && f.password.length >= 8 && f.name.trim().length > 0 && !!f.nationality && !!f.languageCode
})

async function submit() {
  if (!valid.value) return
  error.value = ''
  loading.value = true
  try {
    const f = form.value
    await auth.register({
      loginId: f.loginId.trim(),
      password: f.password,
      name: f.name.trim(),
      nationality: f.nationality,
      languageCode: f.languageCode,
    })
    locale.set(f.languageCode) // 가입한 언어로 시작한다
    router.replace({ name: 'onboarding', params: { step: 1 } })
  } catch (e) {
    const st = e?.response?.status
    error.value = st === 409 ? locale.t('signup.dup') : errorMessage(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="signup">
    <div class="toggle">
      <LangToggle v-model="guestLang" />
    </div>

    <form class="body" @submit.prevent="submit">
      <div class="brand">
        <img :src="logo" alt="FO:TEN" class="logo" />
        <p class="tagline">{{ locale.t('signup.tagline') }}</p>
      </div>

      <div class="form">
        <!-- 이름이 맨 위. 아이디·비밀번호 사이에 두면 비밀번호를 이름 칸에 치는 실수가 나왔다. -->
        <BaseInput v-model="form.name" :label="locale.t('signup.name')" autocomplete="name" :maxlength="50" />
        <BaseInput v-model="form.loginId" :label="locale.t('login.id')" autocomplete="username" :maxlength="50" />
        <BaseInput
          v-model="form.password"
          :label="locale.t('login.pw')"
          type="password"
          autocomplete="new-password"
          :hint="locale.t('signup.pw_hint')"
          :error="pwShort ? locale.t('signup.pw_short') : ''"
        />
        <BaseInput
          v-model="form.nationality"
          :label="locale.t('signup.nationality')"
          type="select"
          :options="nationalityOptions"
          :placeholder="locale.t('common.select')"
        />
        <BaseInput
          v-model="form.languageCode"
          :label="locale.t('signup.language')"
          type="select"
          :options="languageOptions"
          :placeholder="locale.t('common.select')"
          :hint="locale.t('signup.language_hint')"
        />
        <p v-if="error" class="error">{{ error }}</p>
      </div>

      <div class="btns">
        <BaseButton type="submit" :loading="loading" :disabled="!valid">{{ locale.t('signup.submit') }}</BaseButton>
        <BaseButton variant="text" @click="router.push({ name: 'login' })">{{ locale.t('signup.have_account') }}</BaseButton>
      </div>
    </form>
  </main>
</template>

<style scoped>
.signup {
  position: relative;
  min-height: 100dvh;
  background: #fff;
}
.toggle {
  position: absolute;
  right: 20px;
  top: 22px;
}
.body {
  padding: 68px var(--page-x) 40px;
  display: flex;
  flex-direction: column;
}
.brand {
  padding-top: 40px;
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
  padding-top: 32px;
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
</style>
