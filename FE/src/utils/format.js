/*
 * 표기 규칙 — 디자인 시안 기준
 *  - 원화: 12,236,000 (숫자만, '원'은 옆에 따로) 또는 ₩12,236,000 (소비내역·내 정보)
 *  - 외화: 226,592,000 ₫ / 460,000,000₫  — 통화 기호는 국적 상수(NATIONALITIES.symbol)
 *  - 날짜: 2024.09.15 · 월: 2028.08
 *  - D-day: 귀국까지 D-729
 */

export function comma(n) {
  if (n === null || n === undefined || n === '') return '—'
  return Number(n).toLocaleString('ko-KR', { maximumFractionDigits: 0 })
}

export function won(n) {
  return n === null || n === undefined ? '—' : `₩${comma(n)}`
}

export function signed(n) {
  if (n === null || n === undefined) return '—'
  const v = Number(n)
  if (v === 0) return '0'
  return (v > 0 ? '+' : '−') + comma(Math.abs(v))
}

export function dotDate(iso) {
  if (!iso) return '—'
  const [y, m, d] = String(iso).slice(0, 10).split('-')
  return d ? `${y}.${m}.${d}` : `${y}.${m}`
}

export function dotMonth(iso) {
  if (!iso) return '—'
  const [y, m] = String(iso).slice(0, 7).split('-')
  return `${y}.${m}`
}

export function daysUntil(iso) {
  if (!iso) return null
  const target = new Date(iso + 'T00:00:00')
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.round((target - today) / 86_400_000)
}

export function dday(iso) {
  const d = daysUntil(iso)
  if (d === null) return '—'
  return d >= 0 ? `D-${d}` : `D+${-d}`
}

// 2026-09 → { year: 2026, month: 9 }
export function ym(text) {
  const [y, m] = String(text).split('-').map(Number)
  return { year: y, month: m }
}

export function todayMonthDay() {
  const t = new Date()
  return { month: t.getMonth() + 1, day: t.getDate() }
}

export function daysLeftInMonth() {
  const t = new Date()
  const last = new Date(t.getFullYear(), t.getMonth() + 1, 0).getDate()
  return last - t.getDate()
}
