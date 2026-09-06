package com.foten.product.domain;

import java.math.BigDecimal;

// POST /api/roadmap/monthly-plan/deficit-choice(4-6) 결과.
// committed=false 면 구간전환 대기 중이라 계산만 하고 아직 DB에 반영 안 된 상태 —
// 실제 커밋은 4-4(POST /api/rate-conditions/responses)에서 같은 choice를 다시 보낼 때 일어난다.
public record DeficitChoiceResult(BigDecimal monthlySavingAmount, BigDecimal productBaselineAmount, boolean committed) {
}
