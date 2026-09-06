-- FO:TEN 예·적금 로드맵 이력 시드 — 전용 계정
--
-- 02-seed.sql 의 nguyen01/rai01/sok01 은 아직 savings_roadmap 이 없어서 온보딩
-- 흐름(4-1~4-5)만 반복 테스트할 수 있다. 새구간(NEW_SEGMENT) 전환처럼 "구간을 이미
-- 어느 정도 진행한 이력"이 있어야 재현되는 흐름은 기존 3계정과 완전히 분리된 전용
-- 계정 1명(lan01)을 만들어 이 파일 하나로 관리한다 — 다른 팀원 작업(온보딩 흐름
-- 테스트)에 영향을 주지 않기 위함이다.
--
-- lan01 시나리오 — "구간 전환 대기" (flowType=NEW_SEGMENT)
--   로드맵 총 22개월 → 구간분해(로직 v3 §3-2): 12 + 10(마지막)
--   구간1(12개월)이 **어제** 이미 만료됐지만, 아직 POST /api/rate-conditions/responses 의
--   NEW_SEGMENT 분기(만기 이자 계산 → 구간2 생성)가 구현되지 않아 전환 처리가 안 된 상태를
--   그대로 재현한다 — RoadmapQueryServiceImpl.getStatus() 의 판정 조건 그대로:
--     (a) ACTIVE 구간의 end_date < 오늘   (b) cycle_no=1 monthly_saving_plan 존재
--   두 조건만 맞으면 flowType=NEW_SEGMENT 가 뜬다.
--
--   만료 시점을 "어제"로 잡은 이유: getStatus()의 부족액 계산(completedCycles = 오늘
--   기준 로드맵 시작월부터의 달력 개월수 - 1)은 "매달 빠짐없이 대화한다"를 전제로 짠
--   코드라, 구간 만료 후 대화 재개까지 실제 달력상 한 달 이상 비면 그 사이 달들까지
--   전부 "이미 끝난 미납월"로 세버려 부족액이 부풀어 오른다(직접 겪은 이슈: end_date를
--   "한 달 전"으로 잡았을 때 cycle13·14가 둘 다 잡혀 500,000이 아니라 1,000,000이
--   나왔었다). 이 시드는 "매달 대화하는 정상 케이스"만 먼저 검증하는 게 목적이라, 대화가
--   밀리는 기간을 하루로 최소화해서 이 계산 경계에 걸리지 않게 했다 — 여러 달치가 밀렸을
--   때 부족액을 어떻게 셀지는 별도로 논의할 문제로 남겨둔다.
--
--   구간1 적금은 KB Global Star 적금(product_id=3) 1개만 가입시켰다 — 행동기반
--   우대조건 3개(급여이체·카드결제·해외송금)를 전부 충족한다고 응답하면 기본금리 2.00%
--   + 3.00%p = 5.00%(max_rate 5.50 이내)로, 04-seed-product.sql 의 모든 적금 후보 중
--   예상적용금리가 가장 높다 — allocate() 가 금리 내림차순으로 채우므로 실제로도 이 상품이
--   가장 먼저 뽑힌다. 월 납입한도(500,000)에 목표기준액을 맞춰(500,000) 상품 1개로
--   깔끔하게 떨어지게 했다(현금성 저축 0원) — 나중에 만기 이자 계산을 손으로 검산하기 쉽도록.
--
--   손계산(만기 이자, 이자_계산식_결정.md 공식 — 세전):
--     Σ(500,000 × 5.00% × 잔여개월/12), 잔여개월 12..1
--     = 500,000 × 0.05 × (12+11+...+1)/12 = 500,000 × 0.05 × 6.5 = 162,500원
--     세후(15.4% 원천징수) = 162,500 × (1-0.154) ≈ 137,495원
--     만기금(세전, DB 저장값) = 원금 6,000,000 + 162,500 = 6,162,500원
--   NEW_SEGMENT 분기를 구현한 뒤 이 값과 실제 계산 결과가 맞는지 확인하는 용도다.
--
--   구간1의 12개월(cycle 1~12)은 계획대로(500,000원씩) 완납 — 만료 다음 날인 오늘(cycle13)
--   하루만 아직 대화 전이라, GET /api/roadmap/status 는 hasShortfall=false·
--   requiredAmount=500000(순조)으로 응답해야 한다. "구간을 완납했으면 새 구간도 미납
--   없는 기준으로 계산돼야 한다"는 게 이 계정이 검증하려는 것 — 부족액이 섞인 케이스(며칠이
--   아니라 여러 달 대화가 밀렸을 때 completedCycles를 어떻게 셀지)는 위에 적었듯 별도 논의
--   대상으로 남겨둔다.
--
--   한 계정에 "구간 전환 대기"와 "구간 중간(진행 중)·부족액 발생"을 동시에 담을 수 없는
--   이유는 별개다 — flowType 은 저장된 값이 아니라 오늘 날짜와 세그먼트 end_date 를
--   실시간으로 비교해서 정하기 때문에, 세그먼트의 end_date 하나가 "오늘보다 과거"(→
--   NEW_SEGMENT)이면서 동시에 "오늘보다 미래"(→ REGULAR_MONTH)일 수는 없다. 4-6/4-8/4-9
--   처럼 "세그먼트가 아직 진행 중인" 상태가 필요한 엔드포인트를 만들 때는 별도 계정이
--   여전히 필요하다.
--
-- 날짜는 전부 CURDATE() 기준 상대값이라 언제 재적용해도 "어제 구간이 만료된" 상태 그대로
-- 재현된다(단, 매달 1일에 재적용하면 "어제"가 전달로 넘어가 위 계산이 어긋난다 — 그날만
-- 피한다). 자연키가 없는 로드맵 계열 테이블은 이 계정(lan01) 것만 지우고 다시 넣는다
-- (다른 계정에 영향 없음), 나머지(member/stay_info/financial_info/goal/
-- member_rate_condition_response)는 02-seed.sql 관례대로 INSERT IGNORE.

SET NAMES utf8mb4;

-- 로드맵 시작일 = 어제(구간1 만기일) - 12개월. stay_info/savings_roadmap 양쪽에서 똑같이
-- 써야 날짜가 어긋나지 않아서 세션 변수로 한 번만 계산해둔다.
SET @lan01_start := DATE_SUB(DATE_SUB(CURDATE(), INTERVAL 1 DAY), INTERVAL 12 MONTH);

-- ============================================================
-- member / stay_info / financial_info / goal
-- ============================================================
INSERT IGNORE INTO member (login_id, password, name, nationality, language_code) VALUES
    ('lan01', '$2b$10$n29e29y3iQykR13fevsGX.0WxzbNMKsG24pxZWsbbOX0NG4rKwaB2', 'Pham Thi Lan', 'VIETNAM', 'vi');

-- entry_date = 로드맵 start_date 와 동일(온보딩일 = 저축 운용 시작일, 정착 대기 없음).
-- expected_return_date = 로드맵 end_date + 1개월(스키마 주석 규칙 그대로).
INSERT IGNORE INTO stay_info (member_id, visa_type, entry_date, expected_return_date)
SELECT member_id, 'E-9', @lan01_start, DATE_ADD(@lan01_start, INTERVAL 23 MONTH)
FROM member WHERE login_id = 'lan01';

INSERT IGNORE INTO financial_info (member_id, monthly_income, monthly_living_cost, monthly_remittance, current_savings)
SELECT member_id, 2400000, 850000, 350000, 1200000
FROM member WHERE login_id = 'lan01';

-- target_baseline_amount = 500,000 은 이 시드가 직접 고정한 값이다(§0 설계원칙 7 —
-- 실제로는 온보딩 절차가 계산해서 넣어야 하지만 그 절차가 이 계정을 거치지 않으므로
-- 여기서 대신 채운다). monthly_required_saving 은 "죽은 컬럼"이라 초기값만 채우고
-- product 도메인이 이후 갱신한다.
INSERT IGNORE INTO goal (member_id, target_amount, target_currency, target_baseline_amount, monthly_required_saving)
SELECT member_id, 300000000, 'VND', 500000, 500000
FROM member WHERE login_id = 'lan01';

-- 행동기반 우대조건 6개 중 KB Global Star 적금(product_id=3)에 걸리는 3개만 TRUE —
-- 나머지 3개(자동이체/KB스타뱅킹 이체/소중한 날 지정)는 이 상품엔 우대금리가 없어서 FALSE.
INSERT IGNORE INTO member_rate_condition_response (member_id, condition_code, will_meet)
SELECT member_id, c.condition_code, c.will_meet
FROM member m
JOIN (SELECT 'SALARY_TRANSFER' AS condition_code, TRUE AS will_meet UNION ALL
      SELECT 'CARD_PAYMENT', TRUE UNION ALL
      SELECT 'OVERSEAS_REMITTANCE', TRUE UNION ALL
      SELECT 'AUTO_TRANSFER', FALSE UNION ALL
      SELECT 'STARBANKING_TRANSFER', FALSE UNION ALL
      SELECT 'SPECIAL_DAY', FALSE) c ON TRUE
WHERE m.login_id = 'lan01';

-- ============================================================
-- 로드맵 계열 (자연키 없음) — lan01 것만 지우고 다시 넣는다.
-- FK 자식부터: transaction_history → monthly_saving_allocation → monthly_saving_plan
--             → product_subscription → asset_snapshot → roadmap_segment → savings_roadmap
-- ============================================================
DELETE th FROM transaction_history th
JOIN member m ON m.member_id = th.member_id
WHERE m.login_id = 'lan01';

DELETE msa FROM monthly_saving_allocation msa
JOIN monthly_saving_plan msp ON msp.monthly_saving_plan_id = msa.monthly_saving_plan_id
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'lan01';

DELETE msp FROM monthly_saving_plan msp
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'lan01';

DELETE ps FROM product_subscription ps
JOIN member m ON m.member_id = ps.member_id
WHERE m.login_id = 'lan01';

DELETE ans FROM asset_snapshot ans
JOIN savings_roadmap sr ON sr.savings_roadmap_id = ans.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'lan01';

DELETE rs FROM roadmap_segment rs
JOIN savings_roadmap sr ON sr.savings_roadmap_id = rs.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'lan01';

DELETE sr FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'lan01';

-- ------------------------------------------------------------
-- savings_roadmap — start_date = @lan01_start, total_months=22 → end_date = start+22개월
-- ------------------------------------------------------------
INSERT INTO savings_roadmap (member_id, start_date, end_date, total_months)
SELECT member_id, @lan01_start, DATE_ADD(@lan01_start, INTERVAL 22 MONTH), 22
FROM member WHERE login_id = 'lan01';

-- ------------------------------------------------------------
-- roadmap_segment — 구간1: 12개월, end_date = start+12개월 = 어제(이미 만료) → 아직 status='ACTIVE'
-- (전환 처리가 안 된 상태를 재현하는 게 이 시드의 핵심이라 status 를 COMPLETED 로 바꾸지 않는다)
-- ------------------------------------------------------------
INSERT INTO roadmap_segment (savings_roadmap_id, segment_no, planned_months, start_date, end_date, is_last_segment, status)
SELECT sr.savings_roadmap_id, 1, 12, sr.start_date,
       DATE_ADD(sr.start_date, INTERVAL 12 MONTH), FALSE, 'ACTIVE'
FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'lan01';

-- ------------------------------------------------------------
-- product_subscription — KB Global Star 적금(product_id=3) 1건, expected_applied_rate=5.00
-- (기본 2.00 + 급여이체/카드결제/해외송금 우대 각 1.00), 월한도 500,000 스냅샷.
-- 아직 만기 처리 전이라 status='ACTIVE', maturity_amount=NULL.
-- ------------------------------------------------------------
INSERT INTO product_subscription
    (member_id, product_id, segment_id, subscription_role, term_months, start_date, maturity_date,
     expected_applied_rate, monthly_payment_limit_snapshot, status)
SELECT m.member_id, 3, rs.segment_id, 'NEW_SAVINGS', 12, rs.start_date, rs.end_date, 5.00, 500000, 'ACTIVE'
FROM roadmap_segment rs
JOIN savings_roadmap sr ON sr.savings_roadmap_id = rs.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'lan01' AND rs.segment_no = 1;

-- ------------------------------------------------------------
-- monthly_saving_plan — cycle_no 1~12, 매달 500,000원 계획대로 완납(부족액 없음).
-- current_accumulated_fund/cumulative_saving_performance 는 "그 달 납입 전" 스냅샷이라
-- cycle n = 500,000 × (n-1). baseline/required 모두 500,000 로 항상 순조.
-- ------------------------------------------------------------
INSERT INTO monthly_saving_plan
    (savings_roadmap_id, segment_id, plan_month, cycle_no, deficit_choice, monthly_saving_amount,
     recommended_cash_saving, current_accumulated_fund, cumulative_saving_performance,
     baseline_snapshot, required_snapshot)
SELECT sr.savings_roadmap_id, rs.segment_id,
       DATE_FORMAT(DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH), '%Y-%m-01'),
       c.cycle_no, 'NONE', 500000, 0,
       500000 * (c.cycle_no - 1), 500000 * (c.cycle_no - 1), 500000, 500000
FROM savings_roadmap sr
JOIN roadmap_segment rs ON rs.savings_roadmap_id = sr.savings_roadmap_id AND rs.segment_no = 1
JOIN member m ON m.member_id = sr.member_id
JOIN (SELECT 1 AS cycle_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
      SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
      SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12) c ON TRUE
WHERE m.login_id = 'lan01';

-- ------------------------------------------------------------
-- monthly_saving_allocation — 매달 전액(500,000)이 이 적금 하나로 배분(현금성 0원).
-- ------------------------------------------------------------
INSERT INTO monthly_saving_allocation (monthly_saving_plan_id, product_subscription_id, allocated_amount, allocation_order)
SELECT msp.monthly_saving_plan_id, ps.product_subscription_id, 500000, 1
FROM monthly_saving_plan msp
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
JOIN product_subscription ps ON ps.segment_id = msp.segment_id
WHERE m.login_id = 'lan01';

-- ------------------------------------------------------------
-- asset_snapshot — 매달 마감 스냅샷. monthly_payment=500,000(그 달 실제 납입),
-- cash_saving_balance=0(전액 적금으로 들어가 현금성으로 남는 돈 없음).
-- ------------------------------------------------------------
INSERT INTO asset_snapshot (savings_roadmap_id, segment_id, snapshot_month, monthly_payment, cash_saving_balance)
SELECT sr.savings_roadmap_id, rs.segment_id,
       DATE_FORMAT(DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH), '%Y-%m-01'),
       500000, 0
FROM savings_roadmap sr
JOIN roadmap_segment rs ON rs.savings_roadmap_id = sr.savings_roadmap_id AND rs.segment_no = 1
JOIN member m ON m.member_id = sr.member_id
JOIN (SELECT 1 AS cycle_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
      SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
      SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12) c ON TRUE
WHERE m.login_id = 'lan01';

-- ------------------------------------------------------------
-- transaction_history — SAVINGS_PAYMENT 12건. 각 회차 납입일을 sr.start_date와 같은
-- 일자(=@lan01_start의 일자, 매달 그 날짜)로 맞춰서 로드맵 start_date 이후로 확실히
-- 잡히게 한다 — 하루라도 앞선 날짜를 쓰면 sumSavingsPaymentBetween(>= roadmapStart)이
-- 1회차 납입을 빼먹는 경계 버그가 난다(직접 겪음: 매달 5일로 하드코딩했다가 start_date가
-- 6일이라 1회차가 누락돼 부족액이 두 배로 잡혔었다).
-- balance_after 는 이 도메인 계산에서 쓰지 않는 필드라 임의의 그럴듯한 값(300,000)으로 채운다.
-- ------------------------------------------------------------
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, product_subscription_id, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH), INTERVAL 10 HOUR),
       'SAVINGS_PAYMENT', 'OUT', 500000, 300000, ps.product_subscription_id, 'KB Global Star 적금 납입'
FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
JOIN roadmap_segment rs ON rs.savings_roadmap_id = sr.savings_roadmap_id AND rs.segment_no = 1
JOIN product_subscription ps ON ps.segment_id = rs.segment_id
JOIN (SELECT 1 AS cycle_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
      SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
      SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12) c ON TRUE
WHERE m.login_id = 'lan01';
