import { ref } from 'vue'

/*
 * 화면이 쓰는 "오늘".
 *
 * 시연용 계정(demo_clock)은 서버가 회차·부족액·이번 달 소비를 미래 날짜 기준으로 계산한다.
 * 그때 화면만 브라우저 날짜(new Date())를 쓰면 소비내역 월 탭이 2026.09 로, D-day 가 오늘 기준으로
 * 나와 데이터와 어긋난다. 그래서 서버가 내려주는 today(/users/me, /users/me/onboarding)를 한 곳에
 * 두고, 날짜가 필요한 화면은 new Date() 대신 여기 today() 를 쓴다. 서버 값이 없으면(로그인 전,
 * 일반 계정) 브라우저 날짜 그대로다.
 */
const serverToday = ref(null) // 'YYYY-MM-DD' 또는 null

export function setServerToday(iso) {
  serverToday.value = typeof iso === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(iso) ? iso : null
}

// 자정 기준 Date. 서버 값이면 그 날짜의 00:00, 아니면 브라우저의 지금.
export function today() {
  if (serverToday.value) return new Date(serverToday.value + 'T00:00:00')
  return new Date()
}

export function todayIso() {
  if (serverToday.value) return serverToday.value
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export { serverToday }
