import { defineStore } from 'pinia'
import { chatApi } from '../api'

/*
 * 대화 상태.
 *  messages: [{ id, role: 'USER'|'ASSISTANT', contentKo, contentLocal, card, createdAt, pending? }]
 *  card 는 답변에 딸린 그림이다. 서버가 그때 값으로 남긴 스냅샷이라 화면은 다시 계산하지 않는다.
 *  suggestions: 마지막 답변에 딸려 온 선택지 칩. 이력에는 저장되지 않으므로 여기서만 들고 있다.
 *
 *  GET /chat/messages 는 최신순이라 뒤집어서 넣는다.
 *  USER 행은 contentKo 가 null 이고 원문이 contentLocal 에 있다.
 */
export const useChatStore = defineStore('chat', {
  state: () => ({
    messages: [],
    suggestions: [],
    loaded: false,
    sending: false,
    error: '',
    pendingQuestion: '', // 대시보드 CTA 등에서 넘어올 때 자동 전송할 질문
    scrollTop: null, // 다른 화면에 갔다 돌아오믄 되돌릴 대화 스크롤 위치. 없으믄 맨 아래
  }),

  getters: {
    oldestId: (s) => (s.messages.length ? s.messages[0].id : null),
    hasHistory: (s) => s.messages.length > 0,
  },

  actions: {
    async loadHistory() {
      if (this.loaded) return
      const rows = await chatApi.messages(undefined, 30)
      this.messages = rows
        .slice()
        .reverse()
        .map((r) => ({
          id: r.chatMessageId,
          role: r.messageRole,
          contentKo: r.contentKo,
          contentLocal: r.contentLocal,
          card: r.card,
          createdAt: r.createdAt,
        }))
      this.loaded = true
    },

    async loadOlder() {
      if (!this.oldestId) return 0
      const rows = await chatApi.messages(this.oldestId, 30)
      const older = rows
        .slice()
        .reverse()
        .map((r) => ({
          id: r.chatMessageId,
          role: r.messageRole,
          contentKo: r.contentKo,
          contentLocal: r.contentLocal,
          card: r.card,
          createdAt: r.createdAt,
        }))
      this.messages = [...older, ...this.messages]
      return older.length
    },

    async send(text) {
      const message = String(text || '').trim()
      if (!message || this.sending) return
      if (message.length > 500) {
        this.error = 'too_long'
        return
      }
      this.error = ''
      this.sending = true
      this.suggestions = []
      const tempId = `tmp-${Date.now()}`
      this.messages.push({ id: tempId, role: 'USER', contentKo: null, contentLocal: message, createdAt: new Date().toISOString() })
      this.messages.push({ id: `${tempId}-bot`, role: 'ASSISTANT', pending: true })
      try {
        const reply = await chatApi.send(message)
        const bot = this.messages[this.messages.length - 1]
        bot.pending = false
        bot.contentKo = reply.contentKo
        bot.contentLocal = reply.contentLocal
        bot.card = reply.card
        bot.createdAt = new Date().toISOString()
        this.suggestions = reply.suggestions || []
      } catch {
        this.messages.pop() // pending 말풍선 제거
        // 서버 message 는 한국어로 고정이라 담아 두면 화면에서 그대로 새어 나간다.
        // 챗은 실패 이유가 무엇이든 안내가 "다시 물어봐 주세요" 로 같아, 화면이 옮길 키만 남긴다.
        this.error = 'send_failed'
      } finally {
        this.sending = false
      }
    },

    queue(question) {
      this.pendingQuestion = question
    },
  },
})
