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

// 서버 에러 본문은 { message } 하나다. 없으면(400 본문 없음 등) 상태코드로 안내한다.
export function errorMessage(err, fallback = '잠시 후 다시 시도해 주세요.') {
  return err?.response?.data?.message || fallback
}

export default http
