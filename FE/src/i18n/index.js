import ko from './messages/ko.json'
import en from './messages/en.json'
import vi from './messages/vi.json'
import tl from './messages/tl.json'
import th from './messages/th.json'
import id from './messages/id.json'
import si from './messages/si.json'
import mn from './messages/mn.json'
import uz from './messages/uz.json'
import ur from './messages/ur.json'
import km from './messages/km.json'
import zh from './messages/zh.json'
import bn from './messages/bn.json'
import ne from './messages/ne.json'
import my from './messages/my.json'
import ky from './messages/ky.json'
import tg from './messages/tg.json'
import tet from './messages/tet.json'
import lo from './messages/lo.json'

/*
 * 백엔드 Translator.LANGUAGE_NAMES 와 같은 18개. 순서도 그대로 둔다.
 * label 은 그 언어 사용자가 읽는 자기 언어 이름(자칭)이다.
 * 정적 UI 사전(messages)이 아직 없는 언어는 영어로 보인다. 챗봇 답변은 백엔드가 그 언어로 준다.
 */
export const LANGUAGES = [
  { code: 'ko', label: '한국어' },
  { code: 'vi', label: 'Tiếng Việt' },
  { code: 'tl', label: 'Filipino' },
  { code: 'th', label: 'ไทย' },
  { code: 'id', label: 'Bahasa Indonesia' },
  { code: 'si', label: 'සිංහල' },
  { code: 'mn', label: 'Монгол' },
  { code: 'uz', label: 'Oʻzbekcha' },
  { code: 'ur', label: 'اردو' },
  { code: 'km', label: 'ខ្មែរ' },
  { code: 'zh', label: '中文' },
  { code: 'bn', label: 'বাংলা' },
  { code: 'ne', label: 'नेपाली' },
  { code: 'my', label: 'မြန်မာ' },
  { code: 'ky', label: 'Кыргызча' },
  { code: 'tg', label: 'Тоҷикӣ' },
  { code: 'tet', label: 'Tetun' },
  { code: 'lo', label: 'ລາວ' },
  { code: 'en', label: 'English' },
]

// 국적(회원가입 선택) → 목표 통화. 온보딩 목표 금액의 단위와 환율 조회에 쓴다.
// 백엔드는 targetCurrency(3글자)를 받고 통화 목록을 제한하지 않는다.
export const NATIONALITIES = [
  { code: 'VIETNAM', currency: 'VND', symbol: '₫', lang: 'vi', name: '베트남' },
  { code: 'PHILIPPINES', currency: 'PHP', symbol: '₱', lang: 'tl', name: '필리핀' },
  { code: 'THAILAND', currency: 'THB', symbol: '฿', lang: 'th', name: '태국' },
  { code: 'INDONESIA', currency: 'IDR', symbol: 'Rp', lang: 'id', name: '인도네시아' },
  { code: 'SRI_LANKA', currency: 'LKR', symbol: 'Rs', lang: 'si', name: '스리랑카' },
  { code: 'MONGOLIA', currency: 'MNT', symbol: '₮', lang: 'mn', name: '몽골' },
  { code: 'UZBEKISTAN', currency: 'UZS', symbol: 'soʻm', lang: 'uz', name: '우즈베키스탄' },
  { code: 'PAKISTAN', currency: 'PKR', symbol: 'Rs', lang: 'ur', name: '파키스탄' },
  { code: 'CAMBODIA', currency: 'KHR', symbol: '៛', lang: 'km', name: '캄보디아' },
  { code: 'CHINA', currency: 'CNY', symbol: '¥', lang: 'zh', name: '중국' },
  { code: 'BANGLADESH', currency: 'BDT', symbol: '৳', lang: 'bn', name: '방글라데시' },
  { code: 'NEPAL', currency: 'NPR', symbol: 'Rs', lang: 'ne', name: '네팔' },
  { code: 'MYANMAR', currency: 'MMK', symbol: 'K', lang: 'my', name: '미얀마' },
  { code: 'KYRGYZSTAN', currency: 'KGS', symbol: 'сом', lang: 'ky', name: '키르기스스탄' },
  { code: 'TAJIKISTAN', currency: 'TJS', symbol: 'SM', lang: 'tg', name: '타지키스탄' },
  { code: 'TIMOR_LESTE', currency: 'USD', symbol: '$', lang: 'tet', name: '동티모르' },
  { code: 'LAOS', currency: 'LAK', symbol: '₭', lang: 'lo', name: '라오스' },
]

export function nationalityOf(code) {
  return NATIONALITIES.find((n) => n.code === code) || null
}

// 19개 전부. 시연 계정 언어(vi·ne·km)와 ko·en 을 먼저 썼고 나머지는 기계 번역 — 네이티브 검수 전까지 참고용.
export const messages = { ko, en, vi, tl, th, id, si, mn, uz, ur, km, zh, bn, ne, my, ky, tg, tet, lo }
