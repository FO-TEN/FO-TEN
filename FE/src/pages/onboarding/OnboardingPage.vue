<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useOnboardingStore } from '../../stores/onboarding'
import { useDashboardStore } from '../../stores/dashboard'
import { useLocaleStore } from '../../stores/locale'
import { errorKey as httpErrorKey } from '../../api/http'
import { comma, daysUntil } from '../../utils/format'
import AppHeader from '../../components/layout/AppHeader.vue'
import LangSwitch from '../../components/ui/LangSwitch.vue'
import BaseInput from '../../components/ui/BaseInput.vue'
import BaseButton from '../../components/ui/BaseButton.vue'
import UnitSwitch from '../../components/ui/UnitSwitch.vue'
import PotenAvatar from '../../components/ui/PotenAvatar.vue'

/*
 * Figma 01b·02b·03b 온보딩 3단계. 백엔드는 POST /users/me/onboarding 하나라 마지막에 한 번 보낸다.
 * 국적 필드는 가입 때 받으므로 1단계에서 뺐다(백엔드가 온보딩에 국적을 받지 않는다).
 * ?from=me 로 오면 한 단계만 고치는 "수정" 이다 — 세 단계 값을 모두 채워 두고, 고친 단계에서 바로 저장한다.
 * (API 가 세 영역을 한 번에 받으므로 안 고친 값도 함께 보낸다. 그래서 저장 전에 세 단계가 다 유효해야 한다.)
 */
const route = useRoute()
const router = useRouter()
const ob = useOnboardingStore()
const dash = useDashboardStore()
const locale = useLocaleStore()

const step = computed(() => Number(route.params.step))
const fromMe = computed(() => route.query.from === 'me')
const t = (k, v) => locale.t(k, v)

// 오류는 글자가 아니라 키로 들고 있는다. 번역해서 넣어두면 언어를 바꿔도 잡은 시점의 언어에 굳고,
// 서버 message 를 그대로 담으면 한국어라 다른 언어 화면에서 한국어가 샌다.
const errorKey = ref('')
const error = computed(() => (errorKey.value ? t(errorKey.value) : ''))
const loading = ref(false)

onMounted(async () => {
  if (fromMe.value && !ob.entryDate) {
    try {
      const me = await dash.loadMe()
      ob.prefill(me)
    } catch {
      /* 값이 없으면 빈 폼으로 */
    }
  }
})

// ── 1단계: 귀국까지 남은 일수 (날짜만으로 계산 — 백엔드 공식과 겹치지 않는다)
const daysLeft = computed(() => (ob.step1Valid ? daysUntil(ob.expectedReturnDate) : null))

// ── 2·3단계: 금액은 원 또는 본국 통화로 입력. 환율(1원당 외화)은 2단계 진입 때 한 번 받아 둔다.
//    2단계는 칸마다 단위 토글이 있고, 칸 밑에는 항상 반대쪽 통화로 환산한 값을 보여준다.
//    3단계는 입력한 단위의 반대쪽 금액을 아래 상자에 보여준다. 제출은 스토어가 백엔드 단위로 바꿔 보낸다.
watch(
  () => step.value,
  (s) => {
    if (s >= 2 && ob.rate === null) ob.loadRate()
  },
  { immediate: true },
)
const fxDate = computed(() => {
  const d = ob.rateDate
  return d ? `${d.slice(5, 7)}.${d.slice(8, 10)}` : ''
})
// 2단계 네 칸. 칸 밑에는 항상 반대쪽 통화 금액을 보여준다 — 원으로 적으면 "≈ 48,147,268 ₫", 본국 통화로 적으면 "≈ ₩2,500,000".
const financeFields = [
  { key: 'monthlyIncome', label: 'onboarding.income' },
  { key: 'currentSavings', label: 'onboarding.savings', hint: 'onboarding.savings_hint' },
  { key: 'monthlyRemittance', label: 'onboarding.remit' },
  { key: 'monthlyLivingCost', label: 'onboarding.fixed' },
]
const approx = (key) => {
  const v = ob[key]
  if (!ob.rate || v === '' || v === null) return ''
  return ob.financeUnits[key] === 'KRW'
    ? `≈ ${comma(Math.round(Number(v) * ob.rate))} ${ob.currency.symbol}`
    : `≈ ₩${comma(Math.round(Number(v) / ob.rate))}`
}
const financeHint = (f) => [approx(f.key), f.hint ? t(f.hint) : ''].filter(Boolean).join(' · ')

const isKrwUnit = computed(() => ob.targetUnit === 'KRW')
// 입력한 단위의 반대쪽 금액
const converted = computed(() => {
  if (!ob.rate || ob.targetAmount === '' || ob.targetAmount === null) return null
  return isKrwUnit.value ? `${comma(ob.targetAmountForeign)} ${ob.currency.symbol}` : `₩${comma(ob.targetAmountKrw)}`
})

// 수정은 한 단계만 보여주지만 저장은 세 영역을 함께 보내므로 전부 유효해야 한다.
const stepValid = computed(() => (step.value === 1 ? ob.step1Valid : step.value === 2 ? ob.step2Valid : ob.step3Valid))
const canNext = computed(() =>
  fromMe.value ? ob.step1Valid && ob.step2Valid && ob.step3Valid : stepValid.value,
)
// 수정으로 들어왔으면 그 자리에서 저장한다. 처음 가입이면 3단계까지 걸어간 뒤 저장.
const isSave = computed(() => fromMe.value || step.value === 3)

// 로드맵이 시작되면 목표를 못 바꾼다. 서버 message 에는 내부 문구와 회원 번호가
// 들어 있어 그대로 뿌리면 안 되고, 한국어뿐이라 번역도 안 탄다. 코드를 보고 우리 문구를 쓴다.
function setSaveError(e) {
  errorKey.value =
    e?.response?.data?.errorCode === 'ROADMAP_ALREADY_EXISTS' ? 'onboarding.locked' : httpErrorKey(e)
}

function clearError() {
  errorKey.value = ''
}

async function next() {
  clearError()
  if (!isSave.value) {
    router.push({ name: 'onboarding', params: { step: step.value + 1 }, query: route.query })
    return
  }
  loading.value = true
  try {
    await ob.submit()
    dash.invalidate()
    ob.reset()
    router.replace(fromMe.value ? { name: 'me' } : { name: 'home' })
  } catch (e) {
    setSaveError(e)
  } finally {
    loading.value = false
  }
}

function back() {
  if (fromMe.value) router.replace({ name: 'me' })
  else if (step.value > 1) router.replace({ name: 'onboarding', params: { step: step.value - 1 }, query: route.query })
}

const mood = { 1: 'hero', 2: 'wink', 3: 'happy' }
</script>

<template>
  <main class="ob">
    <!-- 단계 표시는 뒤로가기 옆(제목 자리), 언어 토글은 오른쪽 끝. 둘을 오른쪽에 몰면 답답하다.
         수정은 한 단계짜리라 단계 표시와 진행 바를 쓰지 않는다. -->
    <AppHeader :back="step > 1 || fromMe" title-style="medium" @back="back">
      <template #title><span v-if="!fromMe" class="stepno num">{{ step }} / 3</span></template>
      <template #right><LangSwitch /></template>
    </AppHeader>
    <div v-if="!fromMe" class="progress"><div class="fill" :style="{ width: (step / 3) * 100 + '%' }" /></div>

    <form class="body" @submit.prevent="canNext && next()">
      <div class="titlerow">
        <div class="title">
          <h1>{{ t(`onboarding.s${step}.title`) }}</h1>
          <p>{{ t(`onboarding.s${step}.sub`) }}</p>
        </div>
        <PotenAvatar :mood="mood[step]" :size="76" class="poten" />
      </div>

      <!-- 1 · 체류 정보 -->
      <template v-if="step === 1">
        <BaseInput v-model="ob.entryDate" :label="t('onboarding.entry')" type="date" />
        <BaseInput
          v-model="ob.expectedReturnDate"
          :label="t('onboarding.return')"
          type="date"
          :hint="t('onboarding.return_hint')"
          :error="ob.entryDate && ob.expectedReturnDate && ob.entryDate >= ob.expectedReturnDate ? t('onboarding.return_err') : ''"
        />
        <div v-if="daysLeft !== null" class="note">
          <p class="n1">{{ t('onboarding.days_left', { n: comma(daysLeft) }) }}</p>
          <p class="n2">{{ t('onboarding.s1.note') }}</p>
        </div>
      </template>

      <!-- 2 · 수입과 지출 -->
      <template v-else-if="step === 2">
        <!-- 칸마다 원 ↔ 본국 통화 토글. 환율이 없는 통화면 원으로만 받는다(백엔드가 원으로 저장). -->
        <BaseInput
          v-for="f in financeFields"
          :key="f.key"
          v-model="ob[f.key]"
          :label="t(f.label)"
          type="money"
          :hint="financeHint(f)"
        >
          <template #unit>
            <UnitSwitch
              :model-value="ob.financeUnits[f.key]"
              :currency="ob.currency"
              :disabled="!ob.rate"
              @update:model-value="(u) => ob.setFinanceUnit(f.key, u)"
            />
          </template>
        </BaseInput>
        <div class="note">
          <p class="n1">{{ t('onboarding.s2.note1') }}</p>
          <p class="n2">{{ t('onboarding.s2.note2') }}</p>
        </div>
      </template>

      <!-- 3 · 목표 설정 -->
      <template v-else>
        <BaseInput v-model="ob.targetAmount" :label="t('onboarding.target')" type="money">
          <template #unit>
            <!-- 원(기본) ↔ 본국 통화. 환율이 없으면 본국 통화로 고정(백엔드는 본국 통화만 받는다). -->
            <UnitSwitch :model-value="ob.targetUnit" :currency="ob.currency" :disabled="!ob.rate" @update:model-value="ob.setUnit" />
          </template>
        </BaseInput>
        <div class="fx">
          <span class="approx">≈</span>
          <template v-if="converted">
            <span class="krw num">{{ converted }}</span>
            <span class="fxdate">{{ t('onboarding.fx_date', { d: fxDate }) }}</span>
          </template>
          <span v-else-if="!ob.rate" class="krw dim">{{ t('onboarding.fx_later') }}</span>
          <span v-else class="krw dim">{{ isKrwUnit ? `0 ${ob.currency.symbol}` : '₩0' }}</span>
        </div>
      </template>

      <p v-if="error" class="error">{{ error }}</p>

      <div class="bw">
        <BaseButton type="submit" :disabled="!canNext" :loading="loading">
          {{ isSave ? t(fromMe ? 'onboarding.save' : 'onboarding.start') : t('onboarding.next') }}
        </BaseButton>
      </div>

      <!--
        환율 출처. open.er-api.com 무료 티어가 요구하는 표기라 문구와 링크를 그대로 쓴다.
        번역하지 않는다 - 약관이 이 영문 그대로를 요구한다.
        환율을 실제로 쓰는 2·3단계에서만 보이면 된다(1단계는 환율을 안 불러온다).
      -->
      <p v-if="ob.rate" class="fxsrc">
        <a href="https://www.exchangerate-api.com" target="_blank" rel="noopener">Rates By Exchange Rate API</a>
      </p>
    </form>
  </main>
</template>

<style scoped>
.ob {
  min-height: 100dvh;
  background: #fff;
  display: flex;
  flex-direction: column;
}
.stepno {
  font-size: 16px;
  font-weight: 500;
  color: var(--gray-700);
}
.progress {
  height: 3px;
  margin-top: -3px;
  background: var(--border-soft);
  position: sticky;
  top: calc(var(--header-h) - 3px);
  z-index: 11;
}
.fill {
  height: 100%;
  background: var(--surface-primary);
  transition: width 0.25s ease;
}
.body {
  padding: 28px var(--page-x) 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.titlerow {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.title {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}
.title h1 {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.3px;
  line-height: 1.45;
  color: var(--gray-900);
}
.title p {
  font-size: 15px;
  line-height: 1.45;
  color: var(--gray-700);
}
.poten {
  width: 76px !important;
  height: 60px !important;
}
.note {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  border-radius: var(--r-button);
  background: var(--yellow-50);
  color: var(--on-primary);
  line-height: 1.45;
}
.n1 {
  font-size: 16px;
  font-weight: 700;
}
.n2 {
  font-size: 14px;
}
.fx {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-radius: var(--r-input);
  background: var(--gray-100);
  line-height: 1.45;
}
.approx {
  font-size: 14px;
  color: var(--gray-700);
}
.krw {
  flex: 1 0 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--gray-900);
}
.krw.dim {
  color: var(--gray-400);
  font-weight: 500;
  font-size: 15px;
}
.fxdate {
  font-size: 12px;
  color: var(--gray-400);
}
.error {
  font-size: 14px;
  color: var(--red);
}
.fxsrc {
  margin-top: 10px;
  font-size: 11px;
  text-align: center;
  color: var(--gray-400);
}
.fxsrc a {
  color: inherit;
  text-decoration: underline;
}
.bw {
  padding-top: 8px;
}
</style>
