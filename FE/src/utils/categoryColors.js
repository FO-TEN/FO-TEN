/*
 * 카테고리별 소비 그래프 색. 도넛 조각(SpendingDonut)과 대화 카드 범례(SpendingCard)가
 * 같은 값을 써야 그림과 목록이 이어져 읽힌다 — 한쪽만 고치면 조각과 점의 색이 어긋난다.
 *
 * 파스텔 팔레트(사용자 지정)라 tokens.css 의 --cat-* 과는 다른 값이다. 그쪽은
 * CategoryIcon 의 아이콘 배경으로 쓰는 진한 톤이고, 여기는 얇은 링에서도 구분되게 고른 톤이다.
 * 기타만 --gray-300 과 값이 같아 토큰을 그대로 참조한다.
 */
export const CATEGORY_COLORS = {
  식비: '#F5AFAF', // pink
  통신: '#BE9FE1', // purple
  교통: '#71C9CE', // teal
  쇼핑: '#A0C49D', // green
  주거: '#E8B894', // terracotta
  기타: 'var(--gray-300)',
}

// 목록에 없는 카테고리가 들어와도 그래프는 그려져야 한다.
export const CATEGORY_FALLBACK_COLOR = 'var(--gray-300)'
