import http from './http'

/*
 * 백엔드 API 한 곳에 모아둔다. 경로·필드는 노션 "API 명세서"(2026-09-06 기준)와 같다.
 * 화면은 이 함수들만 부르고 axios 를 직접 만지지 않는다.
 */

export const authApi = {
  // 201 → { memberId, loginId, name, nationality, languageCode } · 가입 직후 세션이 생긴다
  register: (body) => http.post('/auth/register', body).then((r) => r.data),
  // 200 → 같은 모양. 401 이면 아이디/비밀번호 불일치(둘을 구분해 주지 않는다)
  login: (loginId, password) => http.post('/auth/login', { loginId, password }).then((r) => r.data),
  logout: () => http.post('/auth/logout'),
}

export const memberApi = {
  // GET /api/users/me — 회원·체류·재무·목표를 한 번에. (신규 API)
  me: () => http.get('/users/me').then((r) => r.data),
}

export const onboardingApi = {
  // { completed, hasResidence, hasFinance, hasGoal }
  status: () => http.get('/users/me/onboarding').then((r) => r.data),
  // 체류·재무·목표를 한 번에. 다시 보내면 덮어쓴다.
  register: (body) => http.post('/users/me/onboarding', body).then((r) => r.data),
}

export const goalApi = {
  // 온보딩 안 끝난 회원이면 404
  diagnosis: () => http.get('/goals/current/diagnosis').then((r) => r.data),
}

export const spendingApi = {
  // GET /api/spending?monthsAgo=0 — 이번 달·지난달 고정/변동 항목별 금액. (신규 API)
  monthly: (monthsAgo = 0) => http.get('/spending', { params: { monthsAgo } }).then((r) => r.data),
}

export const exchangeApi = {
  latest: (currencyCode) => http.get(`/exchange-rates/${currencyCode}`).then((r) => r.data),
  // DB 에 없는 통화면 404 — 온보딩 미리보기에서는 실패해도 진행할 수 있어야 한다
  toKrw: (currencyCode, amount) =>
    http.get(`/exchange-rates/${currencyCode}/krw`, { params: { amount } }).then((r) => r.data),
}

export const roadmapApi = {
  // 상품이 아직 없으면 서버가 오류로 답한다. 화면이 빈 상태로 처리한다.
  composition: () => http.get('/roadmap/segments/current/composition').then((r) => r.data),
  graph: () => http.get('/roadmap/graph').then((r) => r.data),
}

export const chatApi = {
  // { contentKo, contentLocal, suggestions[] } · 500자 초과는 400
  send: (message) => http.post('/chat', { message }).then((r) => r.data),
  // 최신순. before = 이 id 보다 이전 것
  messages: (before, size = 30) =>
    http.get('/chat/messages', { params: { before, size } }).then((r) => r.data),
}
