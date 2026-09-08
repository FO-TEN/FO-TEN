import axios from 'axios'

// 세션 쿠키 기반 인증. withCredentials 가 없으면 JSESSIONID 가 안 붙는다.
const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  withCredentials: true,
  timeout: 120_000, // 챗봇 응답이 5~7초, 환율 즉시 수집이 더해지면 더 걸릴 수 있다
  headers: { 'Content-Type': 'application/json' },
})

// 401 은 어느 API 에서든 나온다(세션 만료). 여기서 한 번만 처리하고 로그인으로 보낸다.
// /api/auth/login 자체의 401(비밀번호 틀림)은 화면이 직접 다뤄야 하므로 제외한다.
let onUnauthorized = null
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn
}

http.interceptors.response.use(
  (res) => res,
  (err) => {
    const status = err.response?.status
    const url = err.config?.url || ''
    if (status === 401 && !url.includes('/auth/login') && onUnauthorized) {
      onUnauthorized()
    }
    return Promise.reject(err)
  },
)

/*
 * 서버 message 대신 화면이 번역해 쓸 i18n 키를 고른다.
 *
 * 서버 message 는 한국어로 고정돼 있어서 19개 언어 화면에 그대로 내보내면 거기서 한국어가 샌다.
 * 로그인·가입·온보딩처럼 사용자가 한국어를 못 읽을 수 있는 자리는 이 함수로 키를 받아 t() 로 옮긴다.
 *
 * 화면마다 뜻이 다른 상태코드(401·409 등)는 여기서 다루지 않는다 — 그건 부르는 쪽이 이미
 * 자기 문구로 갈라 처리하고 있고, 여기서 겹쳐 정하면 두 군데를 봐야 한다.
 */
export function errorKey(err) {
  return err?.response ? 'common.error' : 'common.load_failed'
}

export default http
