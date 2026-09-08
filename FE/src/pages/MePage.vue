<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useDashboardStore } from '../stores/dashboard'
import { useLocaleStore } from '../stores/locale'
import { LANGUAGES } from '../i18n'
import { comma, won, dotDate, dotMonth, dday } from '../utils/format'
import BottomNav from '../components/layout/BottomNav.vue'
import LangSwitch from '../components/ui/LangSwitch.vue'
import PotenAvatar from '../components/ui/PotenAvatar.vue'
import BaseButton from '../components/ui/BaseButton.vue'

/*
 * Figma 12_내 정보(217:1959) — GET /users/me + 환율.
 *  섹션별 "수정" 은 온보딩의 해당 단계로 간다(1 체류 · 2 재무 · 3 목표). 같은 API 로 덮어쓴다.
 *  사용 언어는 변경 API 가 없어 표시만 한다.
 *  "KB스타뱅킹 연동" 문구는 뺐다(그런 기능 없음).
 */
const router = useRouter()
const auth = useAuthStore()
const dash = useDashboardStore()
const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)

onMounted(() => dash.loadMePage())

const me = computed(() => dash.me)
const langLabel = computed(() => {
  const code = me.value?.member?.languageCode || auth.languageCode
  const l = LANGUAGES.find((x) => x.code === code)?.label || code
  return code === 'ko' ? '한국어' : `한국어 · ${l}`
})

// step: 1 체류 · 2 재무 · 3 목표. from=me 면 그 단계만 고치고 바로 저장한다.
function edit(step) {
  router.push({ name: 'onboarding', params: { step }, query: { from: 'me' } })
}
async function logout() {
  await auth.logout() // 대화·대시보드·온보딩 스토어도 여기서 함께 비운다
  router.replace({ name: 'login' })
}
</script>

<template>
  <main class="me">
    <header class="hdr"><h1>{{ t('me.title') }}</h1><LangSwitch /></header>

    <div class="body">
      <div class="profile">
        <PotenAvatar :size="52" />
        <div class="pw">
          <p class="name">{{ me?.member?.name || auth.member?.name }}</p>
          <p class="sub">{{ t('nation.' + (me?.member?.nationality || auth.member?.nationality)) }} · E-9</p>
        </div>
      </div>

      <p v-if="dash.loading && !me" class="err">{{ t('common.loading') }}</p>
      <p v-else-if="dash.errorKey && !me" class="err">{{ t(dash.errorKey) }}</p>

      <!-- 체류 정보 -->
      <section class="card">
        <div class="h"><p class="ht">{{ t('me.residence') }}</p><button type="button" class="edit" @click="edit(1)">{{ t('me.edit') }}</button></div>
        <div class="r"><span class="rl">{{ t('me.entry') }}</span><span class="rv num">{{ dotDate(me?.residence?.entryDate) }}</span></div>
        <div class="r"><span class="rl">{{ t('me.return') }}</span><span class="rv num b">{{ dotDate(me?.residence?.expectedReturnDate) }}</span></div>
        <div class="r"><span class="rl">{{ t('me.until_return') }}</span><span class="rv num b">{{ dday(me?.residence?.expectedReturnDate) }}</span></div>
      </section>

      <!-- 재무 정보 -->
      <section class="card">
        <div class="h"><p class="ht">{{ t('me.finance') }}</p><button type="button" class="edit" @click="edit(2)">{{ t('me.edit') }}</button></div>
        <div class="r"><span class="rl">{{ t('me.income') }}</span><span class="rv num">{{ won(me?.finance?.monthlyIncome) }}</span></div>
        <div class="r"><span class="rl">{{ t('me.savings') }}</span><span class="rv num">{{ won(me?.finance?.currentSavings) }}</span></div>
        <div class="r"><span class="rl">{{ t('me.remit') }}</span><span class="rv num">{{ won(me?.finance?.monthlyRemittance) }}</span></div>
        <div class="r"><span class="rl">{{ t('me.fixed') }}</span><span class="rv num">{{ won(me?.finance?.monthlyLivingCost) }}</span></div>
      </section>

      <!-- 목표 -->
      <section class="card">
        <div class="h"><p class="ht">{{ t('me.goal') }}</p><button type="button" class="edit" @click="edit(3)">{{ t('me.edit') }}</button></div>
        <div class="r"><span class="rl">{{ t('me.target') }}</span><span class="rv num">{{ comma(me?.goal?.targetAmount) }}{{ dash.symbol }}</span></div>
        <div class="r"><span class="rl">{{ t('me.target_krw') }}</span><span class="rv num">{{ won(dash.targetKrw) }}</span></div>
        <div class="r"><span class="rl">{{ t('me.target_when') }}</span><span class="rv num">{{ dotMonth(me?.residence?.expectedReturnDate) }}</span></div>
        <div class="r"><span class="rl">{{ t('me.monthly_plan') }}</span><span class="rv num b">{{ won(me?.goal?.targetBaselineAmount) }}</span></div>
      </section>

      <!-- 사용 언어 (표시만 — 변경 API 없음) -->
      <section class="card">
        <div class="lr"><p class="ht">{{ t('me.language') }}</p><span class="lv">{{ langLabel }}</span></div>
      </section>

      <div class="low">
        <BaseButton variant="text" @click="logout">{{ t('me.logout') }}</BaseButton>
      </div>
    </div>

    <BottomNav />
  </main>
</template>

<style scoped>
.me {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  background: var(--gray-50);
}
.hdr {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--header-h);
  padding: 0 16px 0 20px;
  background: var(--surface-card);
  border-bottom: 1px solid var(--border-soft);
}
.hdr h1 {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.45;
  color: var(--gray-900);
}
.body {
  flex: 1 0 auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
}
.profile {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 8px;
}
.pw {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.45;
  min-width: 0;
}
.name {
  font-size: 20px;
  font-weight: 700;
  color: var(--gray-900);
}
.sub {
  font-size: 16px;
  color: var(--gray-700);
}
.err {
  font-size: 14px;
  color: var(--gray-500);
  text-align: center;
  padding: 12px;
}
.card {
  display: flex;
  flex-direction: column;
  padding: 16px 18px;
  border-radius: var(--r-card);
  background: var(--surface-card);
  border: 1px solid var(--border-soft);
  line-height: 1.45;
}
.h {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 4px;
}
.ht {
  flex: 1 0 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--gray-900);
}
.edit {
  font-size: 14px;
  font-weight: 500;
  color: var(--yellow-link);
}
.r {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  font-size: 16px;
}
.rl {
  flex: 1 0 0;
  color: var(--gray-700);
}
.rv {
  font-weight: 600;
  color: var(--gray-900);
  white-space: nowrap;
}
.rv.b {
  font-weight: 700;
}
.lr {
  display: flex;
  align-items: center;
  gap: 8px;
}
.lv {
  font-size: 16px;
  font-weight: 500;
  color: var(--gray-700);
  white-space: nowrap;
}
.low {
  padding-top: 8px;
}
</style>
