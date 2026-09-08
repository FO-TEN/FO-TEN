<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'
import { useLocaleStore } from '../stores/locale'
import { roadmapApi } from '../api'
import LangSwitch from '../components/ui/LangSwitch.vue'
import BottomNav from '../components/layout/BottomNav.vue'
import PotenAvatar from '../components/ui/PotenAvatar.vue'
import RecommendationCard from '../components/ui/RecommendationCard.vue'
import ConditionChecklist from '../components/ui/ConditionChecklist.vue'
import RoadmapGraphCard from '../components/ui/RoadmapGraphCard.vue'
import SegmentDetailCard from '../components/ui/SegmentDetailCard.vue'
import sendIcon from '../assets/icons/send.svg'

/*
 * Figma 05_대화 · 홈(217:2463).
 *  말풍선  contentKo / contentLocal  — LangSwitch(한국어 ↔ 내 언어)로 그 자리에서 바꾼다. 재호출 없음.
 *  칩      서버 suggestions(labelKo/labelLocal) + 첫 화면 고정 칩 3개(로드맵 만들기 · 소비 내역 보기 · 목표 바꾸기)
 *          이력이 있으면 두 가지 다 안 나온다 — 그때는 로드맵 상태를 보고 첫 칩 하나를 띄운다.
 *  입력    POST /chat (500자 상한 — 서버가 400 을 내므로 여기서도 막는다)
 *  이력    GET /chat/messages 최신순 → 오래된 순으로 뒤집어 그린다
 */
const router = useRouter()
const auth = useAuthStore()
const chat = useChatStore()
const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)

const draft = ref('')
const scroller = ref(null)
const MAX = 500

onMounted(async () => {
  try {
    await chat.loadHistory()
  } catch {
    /* 이력이 없어도 대화는 시작할 수 있다 */
  }
  await loadOpener()
  await restoreOrBottom()
  if (chat.pendingQuestion) {
    const q = chat.pendingQuestion
    chat.pendingQuestion = ''
    await chat.send(q)
  }
})

// 카드를 눌러 다른 화면에 갔다 돌아오면 있던 자리로. 매번 맨 아래로 내려가버리면 보던 곳을 잃는다.
onBeforeUnmount(() => {
  if (scroller.value) chat.scrollTop = scroller.value.scrollTop
})
async function restoreOrBottom() {
  if (chat.scrollTop === null) return scrollBottom()
  const top = chat.scrollTop
  chat.scrollTop = null
  await nextTick()
  // 글꼴·이미지가 늘게 자리를 잡으믄 높이가 바뀜다. 바로 한 번, 잠시 뒤 한 번 더 놓는다.
  const apply = () => {
    if (scroller.value) scroller.value.scrollTop = top
  }
  apply()
  setTimeout(apply, 50)
  setTimeout(apply, 250)
}

// 새 말풍선이 붙을 때만 맨 아래로
watch(() => chat.messages.length, scrollBottom)
async function scrollBottom() {
  await nextTick()
  if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
}

// 말풍선에 보일 글 — 사용자 행은 원문(contentLocal), 봇 행은 화면 언어에 따라
function text(m) {
  if (m.role === 'USER') return m.contentLocal ?? m.contentKo ?? ''
  const content = locale.isKorean ? m.contentKo : (m.contentLocal ?? m.contentKo)
  return content || t('chat.failed')
}
function chipLabel(s) {
  return locale.isKorean ? s.labelKo : s.labelLocal || s.labelKo
}

// 구간 상세 카드가 "지난달 실적"(ACTUAL)이 목표기준액보다 낮다고 보여주는 바로 그 메시지만
// 곤란한 표정으로 — 문구를 뒤져서 "부족액" 같은 단어를 찾는 대신, 카드가 이미 구조화해둔
// 값(SegmentDetailCard.vue가 배지 색을 정할 때 쓰는 것과 같은 diff)으로 판단한다.
function moodFor(m) {
  if (m.card?.type === 'SEGMENT_DETAIL') {
    const bar = m.card.payload?.bars?.find((b) => b.type === 'ACTUAL')
    if (bar && Number(bar.amount) < Number(m.card.payload.baselineAmount)) return 'sorry'
  }
  return 'hero'
}

const showGreeting = computed(() => !chat.hasHistory)
// 로드맵은 화면 이동이 아니라 대화로 만든다. 이미 있으면 서버가 그렇게 답한다.
const staticChips = computed(() => [
  { key: 'roadmap', label: t('chat.chip_roadmap'), go: () => chat.send('내 로드맵 만들기') },
  { key: 'spending', label: t('chat.chip_spending'), go: () => router.push({ name: 'spending' }) },
  { key: 'goal', label: t('chat.chip_goal'), go: () => router.push({ name: 'onboarding', params: { step: 1 }, query: { from: 'me' } }) },
])

// 이력이 있으면 고정 칩도 서버 칩도 안 나와 빈 입력창만 남는다. 매달 오는 사용자가 다 이 경우다.
// 로드맵이 있는지만 보고 첫 칩 하나를 띄운다. 어느 단계인지는 누른 뒤 서버가 이어서 알려준다.
const opener = ref(null)
async function loadOpener() {
  if (chat.suggestions.length || showGreeting.value) return
  try {
    const status = await roadmapApi.status()
    opener.value = status.roadmapExists
      ? { key: 'chat.chip_month', message: '이번 달 상황 알려줘' }
      : { key: 'chat.chip_roadmap', message: '내 로드맵 만들기' }
  } catch {
    /* 상태를 못 읽으면 띄우지 않는다. 잘못 짚느니 없는 편이 낫다 */
  }
}

async function submit() {
  const text = draft.value.trim()
  if (!text || chat.sending) return
  draft.value = ''
  await chat.send(text)
}
async function pickChip(s) {
  await chat.send(s.value)
}

// 첫 칩은 한 번 쓰고 사라진다. 그다음부터는 서버가 내려주는 칩이 이어받는다.
async function useOpener() {
  const message = opener.value.message
  opener.value = null
  await chat.send(message)
}

// 우대조건은 여러 개를 골라 한 번에 보낸다. 서버가 그 종류로 내려주면 버튼 대신 체크박스로 그린다.
const conditionChips = computed(() =>
  chat.suggestions.length && chat.suggestions.every((s) => s.action === 'RATE_CONDITION')
    ? chat.suggestions
    : [],
)
</script>

<template>
  <main class="chatpage">
    <header class="hdr">
      <PotenAvatar :size="28" />
      <h1 class="title">{{ t('chat.title') }}</h1>
      <LangSwitch class="switch" />
    </header>

    <div ref="scroller" class="chat">
      <!-- 첫 인사 (이력이 없을 때만) -->
      <div v-if="showGreeting" class="bot">
        <PotenAvatar :size="32" />
        <div class="wrap">
          <div class="bubble bot-b">{{ t('chat.greeting', { name: auth.firstName }) }}</div>
        </div>
      </div>

      <template v-for="m in chat.messages" :key="m.id">
        <div v-if="m.role === 'USER'" class="user">
          <div class="bubble user-b">{{ text(m) }}</div>
        </div>
        <div v-else class="bot">
          <PotenAvatar :size="32" :mood="moodFor(m)" />
          <div class="wrap">
            <div v-if="m.pending" class="bubble bot-b typing"><span /><span /><span /></div>
            <div v-else class="bubble bot-b">{{ text(m) }}</div>
            <RecommendationCard v-if="m.card && m.card.type === 'RECOMMENDATION'" :payload="m.card.payload" />
            <RoadmapGraphCard v-else-if="m.card && m.card.type === 'ROADMAP'" :payload="m.card.payload" />
            <SegmentDetailCard v-else-if="m.card && m.card.type === 'SEGMENT_DETAIL'" :payload="m.card.payload" />
          </div>
        </div>
      </template>

      <!-- 칩: 서버 선택지가 있으면 그것, 아니면 첫 화면 고정 칩 -->
      <ConditionChecklist
        v-if="conditionChips.length"
        :items="conditionChips"
        :disabled="chat.sending"
        @submit="chat.send($event)"
      />
      <div v-else-if="chat.suggestions.length" class="chips">
        <button v-for="(s, i) in chat.suggestions" :key="i" type="button" class="chip" @click="pickChip(s)">{{ chipLabel(s) }}</button>
      </div>
      <div v-else-if="showGreeting" class="chips">
        <button v-for="c in staticChips" :key="c.key" type="button" class="chip" @click="c.go()">{{ c.label }}</button>
      </div>
      <div v-else-if="opener" class="chips">
        <button type="button" class="chip" @click="useOpener()">{{ t(opener.key) }}</button>
      </div>

      <div v-if="chat.error" class="err-row">
        <PotenAvatar :size="28" mood="surprised" />
        <p class="err">
          {{ chat.error === 'too_long' ? t('chat.too_long', { n: MAX }) : chat.error === 'send_failed' ? t('chat.failed') : chat.error }}
        </p>
      </div>
    </div>

    <form class="inputbar" @submit.prevent="submit">
      <input
        v-model="draft"
        class="field"
        :placeholder="t('chat.placeholder')"
        :maxlength="MAX"
        :disabled="chat.sending"
        autocomplete="off"
      />
      <button type="submit" class="send" :disabled="!draft.trim() || chat.sending" aria-label="전송">
        <img :src="sendIcon" alt="" width="42" height="42" />
      </button>
    </form>

    <BottomNav />
  </main>
</template>

<style scoped>
.chatpage {
  height: 100dvh;
  display: flex;
  flex-direction: column;
  background: var(--gray-50);
}
.hdr {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 8px;
  height: var(--header-h);
  padding: 0 16px 0 20px;
  background: #fff;
  border-bottom: 1px solid var(--border-soft);
}
.title {
  flex: 1 0 0;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.5;
  color: var(--gray-900);
}
.chat {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 20px var(--page-x) 16px;
  background: var(--chat-bg);
}
.bot {
  display: flex;
  align-items: flex-start;
  gap: 4px;
}
.wrap {
  flex: 1 0 0;
  min-width: 0;
  padding-top: 26px;
}
.bubble {
  padding: 13px 16px;
  font-size: 17px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
.bot-b {
  background: #fff;
  color: var(--gray-900);
  border-radius: 4px 18px 18px 18px;
}
.user {
  display: flex;
  justify-content: flex-end;
  padding-left: 60px;
}
.user-b {
  background: var(--gray-900);
  color: #fff;
  border-radius: 18px 4px 18px 18px;
}
.typing {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 50px;
}
.typing span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--gray-400);
  animation: blink 1.2s infinite ease-in-out;
}
.typing span:nth-child(2) {
  animation-delay: 0.2s;
}
.typing span:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0.3;
  }
  40% {
    opacity: 1;
  }
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-left: 32px;
}
.chip {
  padding: 10px 16px;
  border-radius: var(--r-pill);
  background: #fff;
  border: 1px solid var(--border-strong);
  font-size: 16px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--gray-900);
}
.err-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.err {
  font-size: 13px;
  color: var(--red);
  text-align: center;
}
.inputbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 68px;
  padding: 0 20px;
  background: #fff;
  border-top: 1px solid var(--border-soft);
}
.field {
  flex: 1 0 0;
  height: 42px;
  padding: 0 18px;
  border: 0;
  border-radius: var(--r-pill);
  background: var(--gray-100);
  font-size: 16px;
  line-height: 1.5;
  color: var(--gray-900);
}
.send {
  flex: 0 0 auto;
  width: 42px;
  height: 42px;
}
.send:disabled {
  opacity: 0.45;
}
.send img {
  width: 42px;
  height: 42px;
}
</style>
