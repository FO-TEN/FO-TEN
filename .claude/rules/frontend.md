---
paths:
  - "FE/**/*.vue"
  - "FE/**/*.js"
  - "FE/**/*.ts"
---

# 프론트엔드 규칙 (Vue 3 + Vite)

## 폴더 구조 — 3명이 동시에 작업하므로 경계를 지킵니다

```
FE/src/
├─ api/                  # axios 인스턴스와 도메인별 호출 함수
├─ stores/               # Pinia 스토어 (도메인 단위)
├─ router/               # 라우트 정의
├─ components/common/    # 도메인에 안 묶이는 공용 컴포넌트 (버튼, 모달 등)
├─ features/<도메인>/    # 도메인 전용 컴포넌트·컴포저블 (goal, chat, product, exchange)
├─ pages/                # 라우트에 1:1 대응하는 화면
└─ assets/
```

- **`components/common/` 은 여러 도메인이 실제로 함께 쓸 때만** 넣는다. 한 화면에서만 쓰는 컴포넌트는 `features/<도메인>/` 에 둔다.
- 다른 사람의 `features/` 폴더를 건드려야 하면 먼저 말한다. 같은 파일을 동시에 고치면 충돌한다.

## 컴포넌트 작성

- **Composition API + `<script setup>`** 을 쓴다. Options API로 새 컴포넌트를 만들지 않는다.
- 파일명·컴포넌트명은 PascalCase 두 단어 이상: `GoalProgressCard.vue`, `ChatMessageList.vue`. `Card.vue` 처럼 한 단어는 피한다.
- `props` 는 타입과 필수 여부를 명시한다.
- `v-for` 에는 항상 안정적인 `key` 를 준다. 배열 인덱스는 순서가 바뀌면 깨지므로 id를 쓴다.
- 한 컴포넌트가 200줄을 넘어가면 쪼갤 것을 먼저 제안한다.

## API 호출

- 모든 요청은 **`/api` 로 시작하는 상대 경로**로 보낸다.
- `http://localhost:8080` 같은 절대 URL을 하드코딩하지 않는다. 개발은 Vite 프록시가, Docker는 nginx가 백엔드로 넘긴다. 하드코딩하면 Docker와 배포에서 깨진다.
- axios 인스턴스를 하나 만들어 공용으로 쓴다. 컴포넌트마다 `axios.get` 을 직접 부르지 않는다.

```js
// src/api/http.js
import axios from 'axios'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 30000,   // LLM 응답이 길어질 수 있어 넉넉히 잡는다
})
```

- API 호출은 `src/api/` 아래 함수로 감싸고, 컴포넌트는 그 함수를 부른다.
- 로딩·에러 상태를 함께 처리한다. 성공 경로만 만들고 끝내지 않는다. 특히 LLM·환율 API는 느리거나 실패할 수 있다.

## 금액과 통화 표시 — FO:TEN 도메인 규칙

- **금액을 화면에서 계산하지 않는다.** 달성률·필요 저축액·환산액은 전부 백엔드가 계산해 내려준 값을 그대로 표시한다. 프론트에서 다시 계산하면 두 값이 어긋나고, 어느 쪽이 맞는지 알 수 없게 된다.
- 원화와 본국 통화를 **항상 함께** 보여준다. 통화 기호를 하드코딩하지 말고 응답의 통화 코드로 포맷한다.
- 포맷은 `Intl.NumberFormat` 을 쓴다. 직접 정규식으로 콤마를 찍지 않는다.
- 환율은 **고시 시각을 함께 표시**한다. 언제 기준인지 없으면 사용자가 값을 신뢰할 수 없다.

## 상태 관리

- 여러 컴포넌트가 공유하는 상태만 **Pinia** 스토어에 둔다. 한 컴포넌트 안에서만 쓰는 값은 `ref` 로 충분하다.
- 스토어는 도메인 단위로 나눈다: `useGoalStore`, `useChatStore`, `useExchangeRateStore`.
- 스토어에 API 호출 로직을 넣되, axios를 직접 부르지 말고 `src/api/` 함수를 쓴다.

## 라우팅

- Vue Router는 history 모드를 쓴다 (nginx `try_files` 설정이 이에 맞춰져 있음).
- 라우트 경로는 kebab-case: `/goal-detail/:id`
- 인증이 필요한 라우트는 `meta: { requiresAuth: true }` 로 표시하고 네비게이션 가드에서 일괄 처리한다.

## 다국어

기획상 모국어 안내가 핵심 기능입니다. 화면 문자열을 컴포넌트에 직접 박지 말고 처음부터 메시지 키로 분리합니다. 나중에 걷어내려면 화면을 전부 다시 손대야 합니다.

## 스타일

- 컴포넌트 스타일은 `<style scoped>` 안에 둔다.
- 전역 스타일은 한 곳에만 모은다. 컴포넌트에서 전역 셀렉터를 덮어쓰지 않는다.
- **색상·모서리 반경은 `src/styles/tokens.css`의 토큰을 쓴다. 값을 직접 박지 않는다.** 눈대중으로 `border-radius: 10px`, `color: #78716c` 처럼 적으면 화면마다 미묘하게 어긋난다 (실제로 한 번 전체 화면 훑어서 정리한 적 있음).

  ```css
  /* X */
  .card { border-radius: 10px; color: #78716c; }

  /* O */
  .card { border-radius: var(--r-card); color: var(--gray-500); }
  ```

  - 자주 쓰는 반경: 입력창·메뉴 항목 `--r-input`(8px) · 카드·드롭다운 메뉴 `--r-card`(12px) · 버튼 `--r-button`(12px) · 대시보드 히어로 카드 `--r-card-lg`(24px) · CTA 버튼 `--r-cta`(16px) · 프로그레스바·칩·완전히 둥근 원 `--r-pill`.
  - 코너 일부만 둥글 때(막대그래프 위쪽 등)도 토큰으로 조합한다: `border-radius: var(--r-card) var(--r-card) 0 0`.
  - 딱 맞는 토큰이 없으면(아주 작은 체크박스 등) 억지로 끼워 맞추지 말고, 왜 그 값을 그대로 뒀는지 코멘트를 남긴다.
  - 카테고리 색처럼 맞는 토큰이 아예 없으면 컴포넌트 안에 새 hex 값을 적지 말고, `tokens.css`에 기존 팔레트 톤에 맞춰 토큰을 추가한 뒤 참조한다.
- **인라인 SVG 아이콘의 `stroke-width`는 `2.2`로 통일한다** — `assets/icons/` 하위 SVG 전부가 이 값을 쓴다. `CategoryIcon.vue`처럼 컴포넌트 안에 직접 그리는 아이콘도 같은 값을 따른다.

## 금지

- `console.log` 를 커밋에 남기지 않는다.
- `v-html` 은 쓰지 않는다. 꼭 필요하면 먼저 알릴 것 (XSS). **LLM 응답을 `v-html` 로 렌더링하지 않는다.**
- 백엔드 응답 형태를 컴포넌트 안에서 즉석으로 가정하지 않는다. 필드명이 불확실하면 Mapper XML이나 DTO를 먼저 확인한다.
