// 로드맵 소개용 예시 그래프 (로직 v3 §9). 57개월 = 12+12+12+21 고정 데이터
// GET /roadmap/graph 응답과 같은 모양이라 RoadmapGraphCard 에 그대로 넣는다
export const ROADMAP_EXAMPLE = Object.freeze({
  totalMonths: 57,
  segments: [
    { segmentNo: 1, months: 12, status: 'ACTIVE', savingsAmount: 12000000, depositAmount: 0, cashAmount: 300000, interestAmount: 250000 },
    { segmentNo: 2, months: 12, status: 'PLANNED', savingsAmount: 12000000, depositAmount: 12250000, cashAmount: 300000, interestAmount: 600000 },
    { segmentNo: 3, months: 12, status: 'PLANNED', savingsAmount: 12000000, depositAmount: 24860000, cashAmount: 300000, interestAmount: 1000000 },
    { segmentNo: 4, months: 21, status: 'PLANNED', savingsAmount: 21000000, depositAmount: 37850000, cashAmount: 0, interestAmount: 2700000 },
  ],
  finalAmount: 57000000,
  expectedInterestTotal: 4500000,
})
